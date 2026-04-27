import { NextRequest } from 'next/server';

import {
  buildProxyResponse,
  destroySessionAndClearCookie,
  forwardServiceRequest,
  getSessionOrUnauthorized,
} from '../../../../lib/api-proxy';
import { registryServiceOrigin } from '../../../../lib/service-origins';

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }

  const { path } = await context.params;
  const targetUrl = `${registryServiceOrigin}/registry/${path.join('/')}`;
  const forwarded = await forwardServiceRequest(request, targetUrl, lookup.session.accessToken);

  if (forwarded.upstream.status === 401) {
    return destroySessionAndClearCookie(buildProxyResponse(forwarded.upstream, forwarded.responseText), lookup.sessionId);
  }
  return buildProxyResponse(forwarded.upstream, forwarded.responseText);
}

export async function GET(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}