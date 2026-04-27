import { NextRequest, NextResponse } from 'next/server';

import {
  DEMO_SESSION_COOKIE,
  DemoSession,
  demoSessionCookieOptions,
  destroyDemoSession,
  getDemoSession,
} from './demo-session';

export type SessionLookup =
  | { sessionId: string; session: DemoSession }
  | { response: NextResponse };

export async function forwardServiceRequest(
  request: NextRequest,
  targetUrl: string,
  accessToken?: string,
  jsonBody?: unknown,
) {
  const headers = new Headers();
  const accept = request.headers.get('accept');
  if (accept) {
    headers.set('accept', accept);
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  let body: string | undefined;
  if (!['GET', 'HEAD'].includes(request.method)) {
    if (jsonBody !== undefined) {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(jsonBody);
    } else {
      const contentType = request.headers.get('content-type');
      if (contentType) {
        headers.set('Content-Type', contentType);
      }
      const text = await request.text();
      body = text || undefined;
    }
  }

  const upstream = await fetch(targetUrl, {
    method: request.method,
    headers,
    body,
    cache: 'no-store',
  });
  const responseText = await upstream.text();
  let json: unknown = null;
  const contentType = upstream.headers.get('content-type') ?? '';
  if (contentType.includes('application/json') && responseText) {
    json = JSON.parse(responseText);
  }

  return { upstream, responseText, json };
}

export function buildProxyResponse(upstream: Response, responseText: string) {
  const headers = new Headers();
  const contentType = upstream.headers.get('content-type');
  const location = upstream.headers.get('location');
  if (contentType) {
    headers.set('content-type', contentType);
  }
  if (location) {
    headers.set('location', location);
  }
  return new NextResponse(responseText || null, {
    status: upstream.status,
    headers,
  });
}

export function unauthorizedResponse(message = 'Unauthorized') {
  return NextResponse.json({ message }, { status: 401 });
}

export function clearSessionCookie(response: NextResponse) {
  response.cookies.set({
    ...demoSessionCookieOptions,
    name: DEMO_SESSION_COOKIE,
    value: '',
    maxAge: 0,
  });
}

export function destroySessionAndClearCookie(response: NextResponse, sessionId?: string) {
  destroyDemoSession(sessionId);
  clearSessionCookie(response);
  return response;
}

export function getSessionOrUnauthorized(request: NextRequest): SessionLookup {
  const sessionId = request.cookies.get(DEMO_SESSION_COOKIE)?.value;
  const session = getDemoSession(sessionId);
  if (!sessionId || !session) {
    const response = unauthorizedResponse();
    if (sessionId) {
      clearSessionCookie(response);
    }
    return { response };
  }
  return { sessionId, session };
}