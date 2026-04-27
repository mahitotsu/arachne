export type AuthSessionResponse = {
  authenticated: boolean;
};

export async function fetchAuthSession() {
  const res = await fetch('/api/auth/session', { cache: 'no-store' });
  if (!res.ok) {
    return { authenticated: false } satisfies AuthSessionResponse;
  }
  return res.json() as Promise<AuthSessionResponse>;
}

export async function clearAuthSession() {
  await fetch('/api/auth/session', { method: 'DELETE' });
}