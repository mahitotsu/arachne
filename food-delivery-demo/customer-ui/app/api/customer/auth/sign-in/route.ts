import { NextRequest, NextResponse } from 'next/server';

import {
  DEMO_SESSION_COOKIE,
  createDemoSession,
  demoSessionCookieOptions,
} from '../../../../../lib/demo-session';
import { customerServiceOrigin } from '../../../../../lib/service-origins';

type AccessTokenResponse = {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  subject: string;
  displayName: string;
  locale: string;
  scopes: string[];
};

export async function POST(request: NextRequest) {
  const body = await request.text();
  const upstream = await fetch(`${customerServiceOrigin}/api/auth/sign-in`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    cache: 'no-store',
  });
  const responseText = await upstream.text();

  if (!upstream.ok) {
    return new NextResponse(responseText || null, {
      status: upstream.status,
      headers: { 'content-type': upstream.headers.get('content-type') ?? 'text/plain' },
    });
  }

  const payload = JSON.parse(responseText) as AccessTokenResponse;
  const sessionId = createDemoSession(payload.accessToken);
  const response = NextResponse.json({
    authenticated: true,
    displayName: payload.displayName,
    locale: payload.locale,
    scopes: payload.scopes,
  });
  response.cookies.set({
    ...demoSessionCookieOptions,
    name: DEMO_SESSION_COOKIE,
    value: sessionId,
  });
  return response;
}