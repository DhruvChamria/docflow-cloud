import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { MergePanel, type Operation } from './MergePanel';

const mergeOperation: Operation = {
  code: 'pdf.merge', label: 'Merge PDFs', available: true, inputTypes: ['application/pdf'],
  minFiles: 2, maxFiles: 10, description: 'Combine documents.', parameters: []
};

test('accepts a PowerPoint file for PPTX conversion', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => [] } as Response);
  const operation: Operation = {
    code: 'convert.pptx_to_pdf', label: 'PowerPoint to PDF', available: true,
    inputTypes: ['application/vnd.openxmlformats-officedocument.presentationml.presentation'],
    minFiles: 1, maxFiles: 1, description: 'Convert a presentation.', parameters: []
  };
  const user = userEvent.setup();

  render(<MergePanel operations={[operation]} />);
  await user.upload(screen.getByLabelText('Choose PPTX files'), new File(['presentation'], 'demo.pptx', {
    type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
  }));

  expect(screen.getByText(/demo\.pptx/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'PowerPoint to PDF' })).toBeEnabled();
});

test('submits selected files and adds the accepted job', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, headers: new Headers(), json: async () => [] } as Response);
  class FakeRequest {
    static last: FakeRequest;
    upload: { onprogress?: (event: ProgressEvent) => void } = {};
    onload?: () => void; onerror?: () => void; status=202;
    responseText=JSON.stringify({ id: 'job-123', status: 'QUEUED', attempts: 0, error: null,
      expiresAt: '2099-01-01T00:00:00Z', operation: 'pdf.merge', outputSize: 0, outputChecksum: null });
    method='';url='';
    constructor(){FakeRequest.last=this;}
    open(method:string,url:string){this.method=method;this.url=url;}
    setRequestHeader(){}
    send(){this.onload?.();}
  }
  vi.stubGlobal('XMLHttpRequest',FakeRequest as unknown as typeof XMLHttpRequest);
  const user=userEvent.setup();
  render(<MergePanel operations={[mergeOperation]}/>);
  await user.upload(screen.getByLabelText('Choose PDF files'),[
    new File(['%PDF-one'],'one.pdf',{type:'application/pdf'}),
    new File(['%PDF-two'],'two.pdf',{type:'application/pdf'})
  ]);
  await user.click(screen.getByRole('button',{name:'Merge PDFs'}));
  expect(FakeRequest.last.method).toBe('POST');
  expect(FakeRequest.last.url).toBe('/api/v1/jobs');
  expect(await screen.findByText('QUEUED')).toBeInTheDocument();
});

test('renders completed and failed states returned by history polling', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ok:true,headers:new Headers(),json:async()=>[
    {id:'ready',status:'COMPLETED',attempts:1,error:null,expiresAt:'2099-01-01T00:00:00Z',operation:'pdf.merge',outputSize:2048,outputChecksum:'a'.repeat(64)},
    {id:'failed',status:'FAILED',attempts:3,error:'INVALID_OR_UNSUPPORTED_PDF',expiresAt:'2099-01-01T00:00:00Z',operation:'pdf.merge',outputSize:0,outputChecksum:null}
  ]} as Response);
  render(<MergePanel operations={[mergeOperation]}/>);
  expect(await screen.findByText('COMPLETED')).toBeInTheDocument();
  expect(screen.getByText('Invalid, encrypted, or overly large PDF.')).toBeInTheDocument();
  expect(screen.getByText(/SHA-256 aaaaaaaaaaaa/)).toBeInTheDocument();
});
