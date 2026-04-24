'use client';

import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import ReactMarkdown from 'react-markdown';
import Link from 'next/link';

type ConversationMessage = {
  role: string;
  text: string;
};

type OrderLineItem = {
  name: string;
  quantity: number;
  unitPrice: number;
  note: string;
};

type OrderDraft = {
  status: string;
  items: OrderLineItem[];
  subtotal: number;
  total: number;
  etaLabel: string;
  paymentStatus: string;
  paymentMethod: string;
  orderId: string;
};

type RoutingDecision = {
  intent: string;
  selectedSkill: string;
  entryStep: string;
  reason: string;
};

type ServiceTrace = {
  service: string;
  agent: string;
  headline: string;
  detail: string;
  routing?: RoutingDecision | null;
};

type ChatResponse = {
  sessionId: string;
  conversation: ConversationMessage[];
  assistantMessage: string;
  draft: OrderDraft;
  trace: ServiceTrace[];
  routing?: RoutingDecision | null;
  suggestions: string[];
  choices: string[];
};

const EMPTY_DRAFT: OrderDraft = {
  status: 'EMPTY',
  items: [],
  subtotal: 0,
  total: 0,
  etaLabel: '',
  paymentStatus: 'PENDING',
  paymentMethod: '',
  orderId: ''
};

const SESSION_KEY = 'delivery-demo-session-id';
const ACCESS_TOKEN_KEY = 'delivery-demo-access-token';

function formatRoutingValue(value: string) {
  return value
    .split('-')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export default function OrderPage() {
  const router = useRouter();
  const [accessToken, setAccessToken] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [message, setMessage] = useState('');
  const [conversation, setConversation] = useState<ConversationMessage[]>([]);
  const [draft, setDraft] = useState<OrderDraft>(EMPTY_DRAFT);
  const [traceMemory, setTraceMemory] = useState<Record<string, ServiceTrace>>({});
  const [activeTurn, setActiveTurn] = useState<Set<string>>(new Set());
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [choices, setChoices] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const messageListEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (messageListEndRef.current) {
      messageListEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [conversation, loading]);

  useEffect(() => {
    const token = window.localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) {
      router.replace('/');
      return;
    }
    setAccessToken(token);

    const savedSessionId = window.localStorage.getItem(SESSION_KEY);
    if (!savedSessionId) return;

    setSessionId(savedSessionId);
    void fetch(`/api/backend/session/${savedSessionId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(r => r.ok ? r.json() as Promise<ChatResponse> : null)
      .then(payload => {
        if (!payload) return;
        setSessionId(payload.sessionId);
        setConversation(payload.conversation ?? []);
        setDraft(payload.draft ?? EMPTY_DRAFT);
        setSuggestions(payload.suggestions ?? []);
        setChoices(payload.choices ?? []);
      })
      .catch(() => window.localStorage.removeItem(SESSION_KEY));
  }, [router]);

  const totalItems = useMemo(
    () => draft.items.reduce((sum, item) => sum + item.quantity, 0),
    [draft.items]
  );

  const deliveryFee = useMemo(
    () => Math.max(draft.total - draft.subtotal, 0),
    [draft.subtotal, draft.total]
  );

  function withBearer(headersInit?: HeadersInit) {
    const headers = new Headers(headersInit);
    headers.set('Authorization', `Bearer ${accessToken}`);
    return headers;
  }

  function startNewOrder() {
    window.localStorage.removeItem(SESSION_KEY);
    setSessionId('');
    setMessage('');
    setConversation([]);
    setDraft(EMPTY_DRAFT);
    setTraceMemory({});
    setActiveTurn(new Set());
    setSuggestions([]);
    setChoices([]);
    setError('');
  }

  async function sendChat(nextMessage: string) {
    const locale = navigator.languages?.[0] ?? navigator.language ?? '';
    const optimisticConversation: ConversationMessage[] = [
      ...conversation,
      { role: 'user', text: nextMessage }
    ];
    setConversation(optimisticConversation);
    setChoices([]);
    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/backend/chat', {
        method: 'POST',
        headers: withBearer({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ sessionId, message: nextMessage, locale }),
      });
      if (response.status === 401) {
        window.localStorage.removeItem(ACCESS_TOKEN_KEY);
        router.replace('/');
        return;
      }
      if (!response.ok) {
        throw new Error(`chat request failed: ${response.status}`);
      }
      const payload: ChatResponse = await response.json();
      setSessionId(payload.sessionId);
      window.localStorage.setItem(SESSION_KEY, payload.sessionId);
      setConversation(payload.conversation ?? []);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setTraceMemory(prev => {
        const next = { ...prev };
        for (const entry of payload.trace ?? []) {
          next[entry.service] = entry;
        }
        return next;
      });
      setActiveTurn(new Set((payload.trace ?? []).map(e => e.service)));
      setSuggestions(payload.suggestions ?? []);
      setChoices(payload.choices ?? []);
      setMessage('');
    } catch (nextError) {
      setConversation(conversation);
      setError(nextError instanceof Error ? nextError.message : 'chat request failed');
    } finally {
      setLoading(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!message.trim() || loading) return;
    void sendChat(message.trim());
  }

  return (
    <main className="shell">
      {/* Top navigation */}
      <header className="topbar">
        <div className="order-topbar-left">
          <Link href="/home" className="order-back-btn">← ホームへ</Link>
          <div className="brand">
            <span className="brand-icon">🍜</span>
            <span className="brand-name">Arachne Kitchen</span>
            <span className="brand-tagline">single-kitchen cloud delivery</span>
          </div>
        </div>
        <div className="session-pill">
          <span className="session-dot" />
          <span>{sessionId ? `session · ${sessionId.slice(-8)}` : 'new session'}</span>
        </div>
        <button
          type="button"
          className="new-order-btn"
          onClick={startNewOrder}
          disabled={loading}
        >
          ＋ 新しい注文
        </button>
      </header>

      {/* Main workspace */}
      <section className="workspace">
        {/* Chat panel */}
        <div className="panel chat-panel">
          <div className="panel-header">
            <div>
              <p className="panel-label">🗨 Customer Chat</p>
              <h2>自然言語で注文する</h2>
            </div>
          </div>

          <div className="message-list">
            {conversation.length === 0 ? (
              <div className="message system">
                「2人分で辛さ控えめ」「子ども向けで自社エクスプレス」など自然に話しかけてください。
                右のトレースパネルには、単一キッチンの在庫判断や配送レーン選択を含む各エージェントの動きが表示されます。
              </div>
            ) : null}

            {conversation.map((entry, index) => (
              <div
                className={`message ${entry.role === 'assistant' ? 'assistant' : 'user'}`}
                key={`${entry.role}-${index}`}
              >
                <span className="message-role">
                  {entry.role === 'assistant' ? '🤖 Arachne Kitchen order-agent' : '🙋 Customer'}
                </span>
                {entry.role === 'assistant'
                  ? <div className="message-md"><ReactMarkdown>{entry.text}</ReactMarkdown></div>
                  : <p>{entry.text}</p>}
              </div>
            ))}

            {loading && (
              <div className="typing-indicator">
                <span className="typing-dot" />
                <span className="typing-dot" />
                <span className="typing-dot" />
              </div>
            )}

            <div ref={messageListEndRef} />
          </div>

          {choices.length > 0 && (
            <div className="choices">
              {choices.map((choice) => (
                <button
                  key={choice}
                  type="button"
                  className="choice-btn"
                  onClick={() => { setChoices([]); void sendChat(choice); }}
                  disabled={loading}
                >
                  {choice}
                </button>
              ))}
            </div>
          )}

          {suggestions.length > 0 && (
            <div className="suggestions">
              {suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => void sendChat(suggestion)}
                  disabled={loading}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          )}

          <form className="composer" onSubmit={onSubmit}>
            <textarea
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="例: 2人分で辛さ控えめ。自社エクスプレスが使えるならそれで。"
              rows={3}
              disabled={loading}
            />
            <div className="composer-footer">
              <div>
                {error
                  ? <span className="error">⚠ {error}</span>
                  : <span>単一クラウドキッチンの各エージェントが裏で連携中</span>
                }
              </div>
              <button type="submit" className="send-btn" disabled={loading || !message.trim()}>
                送信 →
              </button>
            </div>
          </form>
        </div>

        {/* Sidebar */}
        <aside className="sidebar">
          {/* Live draft */}
          <div className="panel draft-panel">
            <div className="panel-header compact">
              <div>
                <p className="panel-label">🛒 Live Draft</p>
                <h2>{draft.status}</h2>
              </div>
              <span className="badge">{totalItems} items</span>
            </div>

            <ul className="draft-list">
              {draft.items.length === 0
                ? <li className="draft-empty">まだアイテムがありません</li>
                : null}
              {draft.items.map((item) => (
                <li key={`${item.name}-${item.note}`}>
                  <div>
                    <strong>{item.quantity}× {item.name}</strong>
                    <span>{item.note}</span>
                  </div>
                  <em>¥{(item.unitPrice * item.quantity).toFixed(0)}</em>
                </li>
              ))}
            </ul>

            {(draft.items.length > 0 || draft.etaLabel) && (
              <div className="draft-summary">
                {draft.etaLabel
                  ? <div><span>🕐 ETA</span><strong>{draft.etaLabel}</strong></div>
                  : null}
                <div><span>小計</span><strong>¥{draft.subtotal.toFixed(0)}</strong></div>
                {deliveryFee > 0
                  ? <div><span>配送料</span><strong>¥{deliveryFee.toFixed(0)}</strong></div>
                  : null}
                <div className="total-row">
                  <span>合計</span><strong>¥{draft.total.toFixed(0)}</strong>
                </div>
                <div>
                  <span>支払い</span>
                  <strong>
                    {draft.paymentStatus}{draft.paymentMethod ? ` · ${draft.paymentMethod}` : ''}
                  </strong>
                </div>
                {draft.orderId
                  ? <div><span>Order ID</span><strong>{draft.orderId}</strong></div>
                  : null}
              </div>
            )}
          </div>

          {/* Trace panel */}
          <div className="panel trace-panel">
            <div className="panel-header compact">
              <div>
                <p className="panel-label">⚡ Service Mesh Trace</p>
                <h2>裏で動くエージェント</h2>
              </div>
            </div>

            <div className="trace-list">
              {Object.keys(traceMemory).length === 0
                ? <p className="trace-empty">まだトレースがありません</p>
                : null}
              {Object.values(traceMemory).map((entry) => {
                const isActive = activeTurn.has(entry.service);
                const routing = entry.routing;
                return (
                  <article
                    key={entry.service}
                    className={`trace-card${isActive ? ' trace-card--active' : ' trace-card--stale'}`}
                  >
                    <div className="trace-meta">
                      <span className="trace-service">{entry.service}</span>
                      <span className="trace-agent">{entry.agent}</span>
                      {!isActive && <em className="stale-label">前回</em>}
                    </div>
                    <h3>{entry.headline}</h3>
                    {routing ? (
                      <>
                        <div className="trace-route">
                          <span className="trace-chip trace-chip--intent">
                            <span className="trace-chip-label">Intent</span>
                            <span>{formatRoutingValue(routing.intent)}</span>
                          </span>
                          <span className="trace-chip trace-chip--skill">
                            <span className="trace-chip-label">Skill</span>
                            <span>{formatRoutingValue(routing.selectedSkill)}</span>
                          </span>
                          <span className="trace-chip trace-chip--step">
                            <span className="trace-chip-label">Entry</span>
                            <span>{formatRoutingValue(routing.entryStep)}</span>
                          </span>
                        </div>
                        <p className="trace-reason">{routing.reason}</p>
                      </>
                    ) : null}
                    <p>{entry.detail}</p>
                  </article>
                );
              })}
            </div>
          </div>
        </aside>
      </section>
    </main>
  );
}
