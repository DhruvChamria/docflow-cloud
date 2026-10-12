import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import axe from 'axe-core';
import { App } from './App';

const json = (body: unknown, ok = true) => Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);

test('loads and displays the operation catalogue', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(input => {
    const url = String(input);
    if (url.endsWith('/auth/me')) return json({ authenticated: false, email: null });
    return json([{ code: 'pdf.split', label: 'Split PDF', available: true, inputTypes: ['application/pdf'], minFiles: 1, maxFiles: 1, description: 'Split a document.', parameters: [] }]);
  });

  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Split PDF' })).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('API connected');
});

test('offers a retry when the catalogue request fails', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch');
  fetchMock.mockImplementation(input => String(input).endsWith('/auth/me')
    ? json({ authenticated: false, email: null })
    : Promise.reject(new Error('offline')));

  render(<App />);
  const retry = await screen.findByRole('button', { name: 'Retry connection' });
  expect(screen.getByRole('status')).toHaveTextContent('API unavailable');
  await userEvent.click(retry);
  await waitFor(() => expect(fetchMock.mock.calls.filter(([input]) => String(input).endsWith('/operations'))).toHaveLength(2));
});

test('has no detectable accessibility violations in the loaded shell', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(input => String(input).endsWith('/auth/me')
    ? json({ authenticated: false, email: null })
    : json([{ code: 'pdf.split', label: 'Split PDF', available: true, inputTypes: ['application/pdf'], minFiles: 1, maxFiles: 1, description: 'Split a document.', parameters: [] }]));
  const { container }=render(<App />);
  await screen.findByRole('heading',{name:'Split PDF'});
  const result=await axe.run(container,{rules:{'color-contrast':{enabled:false}}});
  expect(result.violations).toEqual([]);
});
