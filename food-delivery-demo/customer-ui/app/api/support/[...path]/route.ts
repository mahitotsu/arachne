import { NextRequest } from 'next/server';

import {
  buildProxyResponse,
  destroySessionAndClearCookie,
  forwardServiceRequest,
  getSessionOrUnauthorized,
} from '../../../../lib/api-proxy';
import { updateDemoSession } from '../../../../lib/demo-session';
import { supportServiceOrigin } from '../../../../lib/service-origins';

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }

  const { path } = await context.params;
  const joinedPath = path.join('/');
  const targetUrl = `${supportServiceOrigin}/api/support/${joinedPath}`;

  let requestBody: unknown = undefined;
  if (joinedPath === 'chat' && request.method === 'POST') {
    const incoming = await request.json() as { message?: string };
    requestBody = {
      message: incoming.message ?? '',
      sessionId: lookup.session.supportSessionId ?? null,
    };
  }

  const forwarded = await forwardServiceRequest(
    request,
    targetUrl,
    lookup.session.accessToken,
    requestBody,
  );

  if (forwarded.upstream.status === 401) {
    return destroySessionAndClearCookie(buildProxyResponse(forwarded.upstream, forwarded.responseText), lookup.sessionId);
  }

  if (
    joinedPath === 'chat' &&
    forwarded.upstream.ok &&
    forwarded.json &&
    typeof forwarded.json === 'object' &&
    'sessionId' in forwarded.json
  ) {
    const payload = forwarded.json as { sessionId?: string };
    updateDemoSession(lookup.sessionId, session => {
      session.supportSessionId = payload.sessionId ?? session.supportSessionId;
    });
  }

  return buildProxyResponse(forwarded.upstream, forwarded.responseText);
}

export async function GET(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}