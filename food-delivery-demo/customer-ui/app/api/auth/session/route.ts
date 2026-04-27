import { NextRequest, NextResponse } from 'next/server';

import {
  DEMO_SESSION_COOKIE,
  demoSessionCookieOptions,
  destroyDemoSession,
  getDemoSession,
} from '../../../../lib/demo-session';

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get(DEMO_SESSION_COOKIE)?.value;
  const session = getDemoSession(sessionId);
  if (!sessionId || !session) {
    const response = NextResponse.json({ authenticated: false });
    if (sessionId) {
      response.cookies.set({
        ...demoSessionCookieOptions,
        name: DEMO_SESSION_COOKIE,
        value: '',
        maxAge: 0,
      });
    }
    return response;
  }
  return NextResponse.json({ authenticated: true });
}

export async function DELETE(request: NextRequest) {
  const sessionId = request.cookies.get(DEMO_SESSION_COOKIE)?.value;
  destroyDemoSession(sessionId);
  const response = new NextResponse(null, { status: 204 });
  response.cookies.set({
    ...demoSessionCookieOptions,
    name: DEMO_SESSION_COOKIE,
    value: '',
    maxAge: 0,
  });
  return response;
}