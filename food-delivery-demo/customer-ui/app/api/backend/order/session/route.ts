import { NextRequest, NextResponse } from 'next/server';

import {
  buildProxyResponse,
  destroySessionAndClearCookie,
  forwardServiceRequest,
  getSessionOrUnauthorized,
} from '../../../../../lib/api-proxy';
import { clearOrderContext } from '../../../../../lib/demo-session';
import { backendOrigin } from '../../../../../lib/service-origins';

export async function GET(request: NextRequest) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }

  if (!lookup.session.orderSessionId) {
    return NextResponse.json({ message: 'No active order session' }, { status: 404 });
  }

  const forwarded = await forwardServiceRequest(
    request,
    `${backendOrigin}/api/order/session/${lookup.session.orderSessionId}`,
    lookup.session.accessToken,
  );

  if (forwarded.upstream.status === 401) {
    return destroySessionAndClearCookie(buildProxyResponse(forwarded.upstream, forwarded.responseText), lookup.sessionId);
  }
  if (forwarded.upstream.status === 404) {
    clearOrderContext(lookup.sessionId);
    return NextResponse.json({ message: 'No active order session' }, { status: 404 });
  }
  if (!forwarded.upstream.ok || !forwarded.json || typeof forwarded.json !== 'object') {
    return buildProxyResponse(forwarded.upstream, forwarded.responseText);
  }

  return NextResponse.json({
    ...(forwarded.json as object),
    snapshot: lookup.session.orderSnapshot ?? null,
  });
}

export async function DELETE(request: NextRequest) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }
  clearOrderContext(lookup.sessionId);
  return new NextResponse(null, { status: 204 });
}