import { NextRequest } from 'next/server';

import {
  buildProxyResponse,
  destroySessionAndClearCookie,
  forwardServiceRequest,
  getSessionOrUnauthorized,
} from '../../../../lib/api-proxy';
import { OrderSnapshot, updateDemoSession } from '../../../../lib/demo-session';
import { backendOrigin } from '../../../../lib/service-origins';

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

type SnapshotProposalItem = NonNullable<OrderSnapshot['pendingProposals']>[number];

function getSuggestRawMessage(body: Record<string, unknown>): string {
  if (typeof body.message === 'string') {
    return body.message;
  }
  if (body.intent && typeof body.intent === 'object') {
    const intent = body.intent as Record<string, unknown>;
    if (typeof intent.rawMessage === 'string') {
      return intent.rawMessage;
    }
  }
  return '';
}

function normalizeSuggestRequest(
  incoming: Record<string, unknown>,
  sessionId: string | null | undefined,
): Record<string, unknown> {
  const { message, intent, ...rest } = incoming;
  const existingIntent = intent && typeof intent === 'object'
    ? { ...(intent as Record<string, unknown>) }
    : {};
  const rawMessage = typeof existingIntent.rawMessage === 'string'
    ? existingIntent.rawMessage
    : typeof message === 'string'
      ? message
      : '';

  return {
    ...rest,
    sessionId,
    intent: {
      ...existingIntent,
      rawMessage,
    },
  };
}

function toSnapshotProposalItems(items: unknown): SnapshotProposalItem[] {
  if (!Array.isArray(items)) {
    return [];
  }
  return items.flatMap(item => {
    if (!item || typeof item !== 'object') {
      return [];
    }
    const candidate = item as Record<string, unknown>;
    return [{
      itemId: String(candidate.itemId ?? candidate.name ?? crypto.randomUUID()),
      name: String(candidate.name ?? ''),
      quantity: Number(candidate.quantity ?? 0),
      unitPrice: Number(candidate.unitPrice ?? 0),
      reason: String(candidate.reason ?? candidate.note ?? ''),
    }];
  });
}

function selectConfirmedItems(
  pendingProposals: SnapshotProposalItem[],
  requestItems: unknown,
) {
  if (!Array.isArray(requestItems) || requestItems.length === 0) {
    return pendingProposals;
  }
  const requestedIds = new Set(
    requestItems.flatMap(item => {
      if (!item || typeof item !== 'object') {
        return [];
      }
      const candidate = item as Record<string, unknown>;
      return typeof candidate.itemId === 'string' ? [candidate.itemId] : [];
    }),
  );
  return pendingProposals.filter(item => requestedIds.has(item.itemId));
}

async function handle(request: NextRequest, context: RouteContext) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }

  const { path } = await context.params;
  const joinedPath = path.join('/');
  const targetUrl = `${backendOrigin}/api/${joinedPath}`;

  let requestBody: unknown = undefined;
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    const incoming = await request.json() as Record<string, unknown>;
    switch (joinedPath) {
      case 'order/suggest':
        requestBody = normalizeSuggestRequest(incoming, lookup.session.orderSessionId);
        break;
      case 'order/confirm-items':
      case 'order/confirm-delivery':
      case 'order/confirm-payment':
        requestBody = {
          ...incoming,
          sessionId: lookup.session.orderSessionId,
        };
        break;
      default:
        requestBody = incoming;
    }
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

  if (forwarded.upstream.ok && forwarded.json && typeof forwarded.json === 'object') {
    const payload = forwarded.json as Record<string, unknown>;
    switch (joinedPath) {
      case 'order/suggest': {
        const body = (requestBody ?? {}) as Record<string, unknown>;
        updateDemoSession(lookup.sessionId, session => {
          session.orderSessionId = typeof payload.sessionId === 'string' ? payload.sessionId : session.orderSessionId;
          session.orderSnapshot = {
            message: getSuggestRawMessage(body),
            suggestSummary: String(payload.summary ?? ''),
            suggestEta: Number(payload.etaMinutes ?? 0),
            pendingProposals: toSnapshotProposalItems(payload.proposals),
            confirmedItems: [],
          };
        });
        break;
      }
      case 'order/confirm-items': {
        const body = (requestBody ?? {}) as Record<string, unknown>;
        updateDemoSession(lookup.sessionId, session => {
          const pendingProposals = session.orderSnapshot?.pendingProposals ?? [];
          session.orderSessionId = typeof payload.sessionId === 'string' ? payload.sessionId : session.orderSessionId;
          session.orderSnapshot = {
            message: session.orderSnapshot?.message ?? '',
            suggestSummary: session.orderSnapshot?.suggestSummary ?? '',
            suggestEta: session.orderSnapshot?.suggestEta ?? 0,
            pendingProposals,
            confirmedItems: selectConfirmedItems(pendingProposals, body.items),
          };
        });
        break;
      }
      case 'order/confirm-delivery':
      case 'order/confirm-payment':
        updateDemoSession(lookup.sessionId, session => {
          session.orderSessionId = typeof payload.sessionId === 'string' ? payload.sessionId : session.orderSessionId;
        });
        break;
      default:
        break;
    }
  }

  return buildProxyResponse(forwarded.upstream, forwarded.responseText);
}

export async function GET(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}