import { useEffect, useState } from 'react';
import { MergePanel, type Operation } from './MergePanel';
import { AuthPanel } from './AuthPanel';

export function App() {
  const [operations, setOperations] = useState<Operation[]>([]);
  const [status, setStatus] = useState('Connecting to API…');
  const [attempt, setAttempt] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('Connecting to API…');
    setFailed(false);
    fetch('/api/v1/operations', { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('API unavailable');
        const data: unknown = await response.json();
        if (!Array.isArray(data) || !data.every(op => typeof op.code === 'string' && typeof op.label === 'string'
          && typeof op.available === 'boolean' && Number.isInteger(op.minFiles) && Number.isInteger(op.maxFiles)
          && typeof op.description === 'string' && Array.isArray(op.parameters))) throw new Error('Invalid catalogue');
        if (!controller.signal.aborted) {
          setOperations(data);
          setStatus('API connected');
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setOperations([]);
          setFailed(true);
          setStatus('API unavailable. Start the backend, then retry.');
        }
      });
    return () => controller.abort();
  }, [attempt]);

  return <main>
    <header><span className="brand">DOCFLOW CLOUD</span><span>DOCUMENT PROCESSING PLATFORM</span></header>
    <section className="hero"><p className="eyebrow">ASYNCHRONOUS DOCUMENT PROCESSING</p>
      <h1>Everyday document tools.<br/><em>A cloud-native foundation.</em></h1>
      <p>Merge PDFs, extract pages and convert documents through a shared asynchronous processing platform.</p>
    </section>
    <aside><strong>Deployment status</strong><p>{operations.filter(op => op.available).length || 'Multiple'} document tools are connected to asynchronous workers. Guest use requires no login; optional accounts provide persistent history. Office conversions use a separate LibreOffice worker. Use ordinary test files: advanced forms, bookmarks and digital signatures are not guaranteed to survive editing.</p></aside>
    <AuthPanel />
    {operations.some(op => op.code === 'pdf.merge' && op.available) && <MergePanel operations={operations.filter(op => op.available)} />}
    <section aria-labelledby="tools"><div className="section-title"><h2 id="tools">Operation catalogue</h2><span role="status">{status}</span></div>
      {failed && <button onClick={() => setAttempt(value => value + 1)}>Retry connection</button>}
      <div className="grid">{operations.map((op, index) => <article key={op.code}>
        <span className="number">0{index + 1}</span><h3>{op.label}</h3>
        <p>Up to {op.maxFiles} {op.maxFiles === 1 ? 'file' : 'files'} per job.</p>
        <span className="badge">{op.available ? 'Available' : 'Planned · not available yet'}</span>
      </article>)}</div>
    </section>
    <footer>Cloud Native Document Conversion and PDF Processing Platform<br/>Asynchronous processing · Guest access supported</footer>
  </main>;
}
