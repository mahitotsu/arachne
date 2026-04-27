const SESSION_TTL_MS = 8 * 60 * 60 * 1000;
const PRUNE_INTERVAL_MS = 5 * 60 * 1000;

export const DEMO_SESSION_COOKIE = 'delivery-demo-ui-session';

export const demoSessionCookieOptions = {
  httpOnly: true,
  sameSite: 'lax' as const,
  secure: process.env.NODE_ENV === 'production',
  path: '/',
  maxAge: SESSION_TTL_MS / 1000,
};

type SnapshotProposalItem = {
  itemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
  reason: string;
};

export type OrderSnapshot = {
  message: string;
  suggestSummary: string;
  suggestEta: number;
  pendingProposals: SnapshotProposalItem[];
  confirmedItems: SnapshotProposalItem[];
};

export type DemoSession = {
  accessToken: string;
  orderSessionId?: string;
  supportSessionId?: string;
  orderSnapshot?: OrderSnapshot;
  touchedAt: number;
};

declare global {
  var __deliveryDemoSessionStore: Map<string, DemoSession> | undefined;
  var __deliveryDemoSessionLastPrunedAt: number | undefined;
}

function sessionStore() {
  if (!globalThis.__deliveryDemoSessionStore) {
    globalThis.__deliveryDemoSessionStore = new Map<string, DemoSession>();
  }
  return globalThis.__deliveryDemoSessionStore;
}

function pruneExpiredSessions() {
  const now = Date.now();
  if (
    globalThis.__deliveryDemoSessionLastPrunedAt &&
    now - globalThis.__deliveryDemoSessionLastPrunedAt < PRUNE_INTERVAL_MS
  ) {
    return;
  }
  globalThis.__deliveryDemoSessionLastPrunedAt = now;
  for (const [sessionId, session] of sessionStore()) {
    if (now - session.touchedAt > SESSION_TTL_MS) {
      sessionStore().delete(sessionId);
    }
  }
}

export function createDemoSession(accessToken: string) {
  pruneExpiredSessions();
  const sessionId = crypto.randomUUID();
  sessionStore().set(sessionId, {
    accessToken,
    touchedAt: Date.now(),
  });
  return sessionId;
}

export function getDemoSession(sessionId: string | undefined) {
  pruneExpiredSessions();
  if (!sessionId) {
    return null;
  }
  const session = sessionStore().get(sessionId);
  if (!session) {
    return null;
  }
  if (Date.now() - session.touchedAt > SESSION_TTL_MS) {
    sessionStore().delete(sessionId);
    return null;
  }
  session.touchedAt = Date.now();
  return session;
}

export function updateDemoSession(
  sessionId: string | undefined,
  updater: (session: DemoSession) => void,
) {
  const session = getDemoSession(sessionId);
  if (!session) {
    return null;
  }
  updater(session);
  session.touchedAt = Date.now();
  return session;
}

export function destroyDemoSession(sessionId: string | undefined) {
  if (!sessionId) {
    return;
  }
  sessionStore().delete(sessionId);
}

export function clearOrderContext(sessionId: string | undefined) {
  updateDemoSession(sessionId, session => {
    delete session.orderSessionId;
    delete session.orderSnapshot;
  });
}

export function clearSupportContext(sessionId: string | undefined) {
  updateDemoSession(sessionId, session => {
    delete session.supportSessionId;
  });
}