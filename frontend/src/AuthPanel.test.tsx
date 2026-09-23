import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { AuthPanel } from './AuthPanel';

test('supports optional account login without hiding guest access', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: false, email: null }) } as Response)
    .mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: true, email: 'student@example.edu' }) } as Response);
  render(<AuthPanel />);
  expect(screen.getByText(/continue as a guest/i)).toBeInTheDocument();
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'student@example.edu' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'example1234' } });
  fireEvent.click(screen.getAllByRole('button', { name: 'Log in' }).at(-1)!);

  expect(await screen.findByText(/signed in as/i)).toHaveTextContent('student@example.edu');
  expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }));
});
