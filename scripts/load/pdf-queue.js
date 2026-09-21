import http from 'k6/http';
import { check, sleep } from 'k6';

const base = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const first = open('../../backend/pdf-worker/target/demo-inputs/document-a.pdf', 'b');
const second = open('../../backend/pdf-worker/target/demo-inputs/document-b.pdf', 'b');
const boundary = 'docflow-k6-boundary';
function encode(value) { const bytes = new Uint8Array(value.length); for (let index = 0; index < value.length; index++) bytes[index] = value.charCodeAt(index); return bytes; }
function multipart() {
  const chunks = [];
  function field(name, value) { chunks.push(encode(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`)); }
  function file(name, value) {
    chunks.push(encode(`--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="${name}"\r\nContent-Type: application/pdf\r\n\r\n`));
    chunks.push(new Uint8Array(value)); chunks.push(encode('\r\n'));
  }
  file('a.pdf', first); file('b.pdf', second); field('operation', 'pdf.merge'); chunks.push(encode(`--${boundary}--\r\n`));
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0); const body = new Uint8Array(length); let offset = 0;
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length; }
  return body.buffer;
}
const body = multipart();

export const options = {
  scenarios: { queue_backlog: { executor: 'constant-vus', vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s' } },
  thresholds: { http_req_failed: ['rate<0.02'], http_req_duration: ['p(95)<2000'], checks: ['rate>0.98'] },
};

export default function () {
  const response = http.post(`${base}/api/v1/jobs`, body,
    { headers: { 'X-Requested-With': 'docflow', 'Content-Type': `multipart/form-data; boundary=${boundary}` }, timeout: '15s' });
  check(response, { 'job accepted': result => result.status === 202 });
  if (response.status !== 202) { sleep(1); return; }
  const id = response.json('id');
  for (let attempt = 0; attempt < 20; attempt++) {
    sleep(1);
    const status = http.get(`${base}/api/v1/jobs/${id}`, { timeout: '10s' });
    if (status.status === 200 && status.json('status') === 'COMPLETED') { check(status, { 'job completed': () => true }); return; }
    if (status.status === 200 && status.json('status') === 'FAILED') break;
  }
  check(null, { 'job completed': () => false });
}
