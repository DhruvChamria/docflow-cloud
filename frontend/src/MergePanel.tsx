import { useEffect, useRef, useState } from 'react';

export type Parameter = { name: string; label: string; type: 'text' | 'password' | 'select'; required: boolean; help: string; options: string[]; defaultValue: string };
export type Operation = { code: string; label: string; available: boolean; inputTypes: string[]; minFiles: number; maxFiles: number; description: string; parameters: Parameter[] };
type Job = { id: string; status: string; attempts: number; error: string | null; expiresAt: string; operation: string };
const active = (job: Job) => ['CREATED', 'QUEUED', 'PROCESSING', 'RETRYING'].includes(job.status);
const stage = (status: string) => status === 'CREATED' ? 0 : status === 'QUEUED' || status === 'RETRYING' ? 1 : status === 'PROCESSING' ? 2 : status === 'COMPLETED' ? 3 : -1;

export function MergePanel({ operations }: { operations: Operation[] }) {
  const [operationCode, setOperationCode] = useState(operations[0].code);
  const defaults = (item: Operation) => Object.fromEntries(item.parameters.map(parameter => [parameter.name, parameter.defaultValue]));
  const [parameters, setParameters] = useState<Record<string, string>>(defaults(operations[0]));
  const [files, setFiles] = useState<File[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState('');
  const [historyError, setHistoryError] = useState('');
  const mounted = useRef(true);
  const operation = operations.find(item => item.code === operationCode) || operations[0];
  const acceptsImages = operation.inputTypes.some(type => type.startsWith('image/'));
  const acceptsPdf = operation.inputTypes.includes('application/pdf');
  const acceptsText = operation.inputTypes.some(type => type.startsWith('text/'));
  const acceptsOffice = operation.inputTypes.some(type => type.includes('officedocument') || type.includes('opendocument'));
  const mixedInputs = acceptsImages && acceptsPdf;
  const officeLabel = operation.code.includes('pptx') ? 'PPTX' : operation.code.includes('odt') ? 'ODT' : operation.code.includes('odp') ? 'ODP' : operation.code.includes('ods') ? 'ODS' : 'DOCX';
  const acceptAttribute = acceptsText ? `${operation.inputTypes.join(',')},.txt,.md,.markdown,.html,.htm` : acceptsOffice ? `${operation.inputTypes.join(',')},.${officeLabel.toLowerCase()}` : mixedInputs ? 'application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png' : acceptsImages ? 'image/jpeg,image/png,.jpg,.jpeg,.png' : 'application/pdf,.pdf';

  useEffect(() => {
    mounted.current = true; let stopped = false; let timer: ReturnType<typeof setTimeout>; const controller = new AbortController();
    async function refresh() {
      try {
        const response = await fetch('/api/v1/jobs', { signal: controller.signal });
        if (!response.ok) throw new Error('Job history unavailable. The page will retry automatically.');
        const result: Job[] = await response.json(); if (!stopped) { setJobs(result); setHistoryError(''); }
      } catch (e) { if (!stopped) setHistoryError(e instanceof Error ? e.message : 'Connection unavailable'); }
      finally { if (!stopped) timer = setTimeout(refresh, 2000); }
    }
    void refresh();
    return () => { stopped = true; mounted.current = false; controller.abort(); clearTimeout(timer); };
  }, []);

  function addFiles(selected: File[]) {
    const isImage=(file:File)=>['image/jpeg','image/png'].includes(file.type) || /\.(jpe?g|png)$/i.test(file.name);
    const isPdf=(file:File)=>file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
    const isText=(file:File)=>file.type.startsWith('text/') || /\.(txt|md|markdown|html?|xhtml)$/i.test(file.name);
    const isOffice=(file:File)=>file.type.includes('officedocument') || file.type.includes('opendocument') || /\.(docx|pptx|odt|odp|ods)$/i.test(file.name);
    const accepted = selected.filter(file => (acceptsImages&&isImage(file))||(acceptsPdf&&isPdf(file))||(acceptsText&&isText(file))||(acceptsOffice&&isOffice(file)));
    setFiles(previous => operation.maxFiles === 1 ? accepted.slice(0, 1) : [...previous, ...accepted].slice(0, operation.maxFiles));
    setError(accepted.length !== selected.length ? `Only ${acceptsText ? 'text files' : acceptsOffice ? `${officeLabel} files` : mixedInputs ? 'PDF, JPG and PNG files' : acceptsImages ? 'JPG and PNG images' : 'PDF files'} are accepted for this operation.` : '');
  }

  function submit() {
    if (busy) return; setError('');
    if (files.length < operation.minFiles || files.length > operation.maxFiles) {
      setError(`Choose ${operation.minFiles === operation.maxFiles ? operation.minFiles : `${operation.minFiles} to ${operation.maxFiles}`} PDF${operation.maxFiles === 1 ? '' : 's'}.`); return;
    }
    for (const parameter of operation.parameters)
      if (parameter.required && !(parameters[parameter.name] ?? parameter.defaultValue).trim()) { setError(`${parameter.label} is required.`); return; }
    const perFileLimit = acceptsText ? 1 : 25;
    if (files.some(file => file.size > perFileLimit * 1024 * 1024) || files.reduce((sum, file) => sum + file.size, 0) > 90 * 1024 * 1024) {
      setError(acceptsText ? 'Maximum 1 MiB for text conversion.' : 'Maximum 25 MiB per file and 90 MiB total.'); return;
    }
    setBusy(true); setProgress(0);
    const data = new FormData(); files.forEach(file => data.append('files', file)); data.append('operation', operation.code);
    operation.parameters.forEach(parameter => data.append(parameter.name, parameters[parameter.name] ?? parameter.defaultValue));
    const xhr = new XMLHttpRequest(); xhr.open('POST', '/api/v1/jobs'); xhr.setRequestHeader('X-Requested-With', 'docflow');
    xhr.upload.onprogress = event => { if (event.lengthComputable && mounted.current) setProgress(Math.round(event.loaded * 100 / event.total)); };
    xhr.onload = () => {
      try {
        const result = JSON.parse(xhr.responseText || '{}');
        if (xhr.status !== 202) throw new Error(result.detail || `Upload failed (${xhr.status}).`);
        if (mounted.current) { setJobs(previous => [result, ...previous.filter(item => item.id !== result.id)]); setFiles([]); setProgress(100); }
      } catch (e) { if (mounted.current) setError(`${e instanceof Error ? e.message : 'Upload interrupted.'} Check job history before resubmitting.`); }
      finally { if (mounted.current) setBusy(false); }
    };
    xhr.onerror = () => { if (mounted.current) { setError('Upload interrupted. Check job history before resubmitting.'); setBusy(false); } };
    xhr.send(data);
  }

  async function cancel(job: Job) {
    setError('');
    const response = await fetch(`/api/v1/jobs/${job.id}/cancel`, { method: 'POST', headers: { 'X-Requested-With': 'docflow' } });
    if (!response.ok) { const problem = await response.json().catch(() => ({})); setError(problem.detail || 'This job can no longer be cancelled.'); return; }
    const cancelled: Job = await response.json(); setJobs(previous => previous.map(item => item.id === cancelled.id ? cancelled : item));
  }
  async function remove(job: Job) {
    setError('');const response=await fetch(`/api/v1/jobs/${job.id}`,{method:'DELETE',headers:{'X-Requested-With':'docflow'}});
    if(!response.ok){const problem=await response.json().catch(()=>({}));setError(problem.detail||'Job deletion failed.');return;}
    setJobs(previous=>previous.filter(item=>item.id!==job.id));
  }

  function move(index: number, delta: number) { const copy = [...files]; [copy[index], copy[index + delta]] = [copy[index + delta], copy[index]]; setFiles(copy); }
  const labelFor = (code: string) => operations.find(item => item.code === code)?.label || code;

  return <section className="merge-panel" aria-labelledby="tools-title">
    <h2 id="tools-title">PDF tools</h2>
    <label>Operation <select value={operation.code} disabled={busy} onChange={event => { const next=operations.find(item=>item.code===event.target.value) || operations[0]; setOperationCode(next.code); setFiles([]); setParameters(defaults(next)); setError(''); }}>
      {operations.map(item => <option value={item.code} key={item.code}>{item.label}</option>)}</select></label>
    <p>{operation.description} Files and results expire after 24 hours.</p>
    {operation.parameters.map(parameter => <div className="parameter" key={parameter.name}>
      <label>{parameter.label} {parameter.type === 'select'
        ? <select value={parameters[parameter.name] ?? parameter.defaultValue} disabled={busy} onChange={event => setParameters(previous => ({ ...previous, [parameter.name]: event.target.value }))}>
            {parameter.options.map(option => <option value={option} key={option}>{option}{parameter.name === 'angle' ? '°' : ''}</option>)}</select>
        : <input type={parameter.type === 'password' ? 'password' : 'text'} autoComplete={parameter.type === 'password' ? 'new-password' : undefined} value={parameters[parameter.name] ?? parameter.defaultValue} maxLength={parameter.type === 'password' ? 128 : 5000} disabled={busy} placeholder={parameter.name === 'pages' ? 'e.g. 1,3-5' : ''} required={parameter.required}
            onChange={event => setParameters(previous => ({ ...previous, [parameter.name]: event.target.value }))} />}</label>
      <p className="hint">{parameter.help}</p>
    </div>)}
    <p>Local development only: use non-sensitive test documents. No login required; this browser session owns its jobs.</p>
    <div className={`drop-zone ${dragging ? 'dragging' : ''}`} onDragOver={event => { event.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)}
      onDrop={event => { event.preventDefault(); setDragging(false); addFiles(Array.from(event.dataTransfer.files)); }}>
      <label className="picker">Choose {acceptsText ? 'text' : acceptsOffice ? officeLabel : mixedInputs ? 'PDF and image' : acceptsImages ? 'image' : 'PDF'} files <input type="file" accept={acceptAttribute} multiple={operation.maxFiles > 1} disabled={busy}
        onChange={event => { addFiles(Array.from(event.target.files || [])); event.target.value = ''; }} /></label>
      <span>or drag and drop {acceptsText ? 'one UTF-8 text document' : acceptsOffice ? `one ${officeLabel} document` : mixedInputs ? 'the PDF first, then a JPG/PNG watermark' : acceptsImages ? 'JPG/PNG images' : 'PDF files'} here</span>
    </div>
    <ol className="file-list">{files.map((file, index) => <li key={`${index}-${file.name}`}>
      <span>{file.name} <small>({(file.size / 1024).toFixed(0)} KB)</small></span>
      {operation.code === 'pdf.merge' && <><button disabled={busy || index === 0} onClick={() => move(index, -1)} aria-label={`Move ${file.name} up`}>↑</button>
      <button disabled={busy || index === files.length - 1} onClick={() => move(index, 1)} aria-label={`Move ${file.name} down`}>↓</button></>}
      <button disabled={busy} onClick={() => setFiles(files.filter((_, i) => i !== index))} aria-label={`Remove ${file.name}`}>Remove</button>
    </li>)}</ol>
    <button disabled={busy || files.length < operation.minFiles || files.length > operation.maxFiles} onClick={submit}>{busy ? `Uploading ${progress}%…` : operation.label}</button>
    {busy && <progress value={progress} max="100" aria-label="Upload progress">{progress}%</progress>}
    {error && <p role="alert" className="error">{error}</p>}
    <h3>Your recent jobs</h3>
    {historyError && <p role="status" className="error">{historyError}</p>}
    <p className="hint">Keep this browser’s cookies to access your results. Other browsers cannot access these jobs.</p>
    {jobs.length === 0 && <p>No jobs submitted in this browser yet.</p>}
    <div aria-live="polite">{jobs.map(job => <article className="job" key={job.id}>
      <div><strong>{job.status}</strong> · {labelFor(job.operation)} · attempt {job.attempts}/3<br/><small>{job.id}</small><br/>
        <small>{Date.parse(job.expiresAt) <= Date.now() ? 'Download expired' : 'Expires'}: {new Date(job.expiresAt).toLocaleString()}</small>
        <ol className="timeline" aria-label="Job progress">{['Uploaded','Queued','Processing','Ready'].map((name,index) => <li className={stage(job.status) >= index ? 'done' : ''} key={name}>{name}</li>)}</ol></div>
      {active(job) && <div><span>{job.status === 'RETRYING' ? 'Temporary failure — waiting before retry…' : job.status === 'QUEUED' ? 'Queued for the worker…' : job.status === 'CREATED' ? 'Finishing upload…' : 'Processing your PDF…'}</span>
        <button className="secondary" onClick={() => void cancel(job)}>Cancel</button></div>}
      {job.status === 'FAILED' && <span className="error">{job.error === 'INVALID_PAGE_OPTIONS' ? 'Page selection does not fit this PDF.' : job.error === 'INVALID_OR_UNSUPPORTED_PDF' ? 'Invalid, encrypted, or overly large PDF.' : job.error === 'UPLOAD_FAILED' ? 'Upload failed. Please try again.' : 'Processing failed after recovery attempts.'}</span>}
      {job.status === 'CANCELLED' && <span>Cancelled; temporary files are being removed.</span>}
      {job.status === 'COMPLETED' && Date.parse(job.expiresAt) > Date.now() && <a href={`/api/v1/jobs/${job.id}/download`}>Download result</a>}
      {job.status === 'EXPIRED' && <span>Files removed</span>}
      {!active(job)&&job.status!=='EXPIRED'&&<button className="secondary" onClick={()=>void remove(job)}>Delete job and files</button>}
    </article>)}</div>
  </section>;
}
