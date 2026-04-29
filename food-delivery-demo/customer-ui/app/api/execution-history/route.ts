import { NextRequest, NextResponse } from 'next/server';

import { getSessionOrUnauthorized } from '../../../lib/api-proxy';
import {
  backendOrigin,
  deliveryServiceOrigin,
  menuServiceOrigin,
  paymentServiceOrigin,
  supportServiceOrigin,
} from '../../../lib/service-origins';

export type HistoryEvent = {
  sequence: number;
  occurredAt: string;
  category: string;
  service: string;
  component: string;
  operation: string;
  outcome: string;
  durationMs: number;
  headline: string;
  detail: string;
  usage?: {
    inputTokens: number;
    outputTokens: number;
    cacheReadTokens: number;
    cacheWriteTokens: number;
  };
  skills?: string[];
};

export type ExecutionHistoryResponse = {
  orderSessionId: string | null;
  events: HistoryEvent[];
};

type ServiceHistoryResponse = {
  sessionId: string;
  events: HistoryEvent[];
};

async function fetchHistory(
  url: string,
  accessToken: string,
): Promise<HistoryEvent[]> {
  try {
    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: 'no-store',
    });
    if (!res.ok) {
      return [];
    }
    const body = (await res.json()) as ServiceHistoryResponse;
    return body.events ?? [];
  } catch {
    return [];
  }
}

export async function GET(request: NextRequest) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) {
    return lookup.response;
  }

  const { session } = lookup;
  const { orderSessionId, supportSessionId, accessToken } = session;

  if (!orderSessionId) {
    const body: ExecutionHistoryResponse = { orderSessionId: null, events: [] };
    return NextResponse.json(body);
  }

  const [orderEvents, menuEvents, deliveryEvents, paymentEvents, supportEvents] = await Promise.all([
    fetchHistory(`${backendOrigin}/api/order/execution-history/${orderSessionId}`, accessToken),
    fetchHistory(`${menuServiceOrigin}/internal/menu/execution-history/${orderSessionId}`, accessToken),
    fetchHistory(`${deliveryServiceOrigin}/internal/delivery/execution-history/${orderSessionId}`, accessToken),
    fetchHistory(`${paymentServiceOrigin}/internal/payment/execution-history/${orderSessionId}`, accessToken),
    supportSessionId
      ? fetchHistory(`${supportServiceOrigin}/api/support/execution-history/${supportSessionId}`, accessToken)
      : Promise.resolve<HistoryEvent[]>([]),
  ]);

  const allEvents = [
    ...orderEvents,
    ...menuEvents,
    ...deliveryEvents,
    ...paymentEvents,
    ...supportEvents,
  ].sort((a, b) => {
    const timeDiff = a.occurredAt.localeCompare(b.occurredAt);
    if (timeDiff !== 0) return timeDiff;
    return a.sequence - b.sequence;
  });

  const body: ExecutionHistoryResponse = {
    orderSessionId,
    events: allEvents,
  };
  return NextResponse.json(body);
}
