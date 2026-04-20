'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';

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

type ServiceTrace = {
  service: string;
  agent: string;
  headline: string;
  detail: string;
};

type ChatResponse = {
  sessionId: string;
  conversation: ConversationMessage[];
  assistantMessage: string;
  draft: OrderDraft;
  trace: ServiceTrace[];
  suggestions: string[];
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

export default function HomePage() {
  const [sessionId, setSessionId] = useState('');
  const [message, setMessage] = useState('');
  const [conversation, setConversation] = useState<ConversationMessage[]>([]);
  const [draft, setDraft] = useState<OrderDraft>(EMPTY_DRAFT);
  const [trace, setTrace] = useState<ServiceTrace[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const savedSessionId = window.localStorage.getItem('delivery-demo-session-id');
    if (!savedSessionId) {
      return;
    }
    setSessionId(savedSessionId);
    void fetch(`/api/backend/session/${savedSessionId}`)
      .then((response) => response.ok ? response.json() as Promise<ChatResponse> : null)
      .then((payload) => {
        if (!payload) {
          return;
        }
        setConversation(payload.conversation ?? []);
        setDraft(payload.draft ?? EMPTY_DRAFT);
        setSuggestions(payload.suggestions ?? []);
      })
      .catch(() => {
        window.localStorage.removeItem('delivery-demo-session-id');
      });
  }, []);

  const totalItems = useMemo(
    () => draft.items.reduce((sum, item) => sum + item.quantity, 0),
    [draft.items]
  );

  async function sendChat(nextMessage: string) {
    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/backend/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ sessionId, message: nextMessage })
      });
      if (!response.ok) {
        throw new Error(`chat request failed: ${response.status}`);
      }
      const payload: ChatResponse = await response.json();
      setSessionId(payload.sessionId);
      window.localStorage.setItem('delivery-demo-session-id', payload.sessionId);
      setConversation(payload.conversation ?? []);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setTrace(payload.trace ?? []);
      setSuggestions(payload.suggestions ?? []);
      setMessage('');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'chat request failed');
    } finally {
      setLoading(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!message.trim() || loading) {
      return;
    }
    void sendChat(message.trim());
  }

  return (
    <main className="shell">
      <section className="hero">
        <div>
          <p className="eyebrow">Arachne x Spring Boot</p>
          <h1>Food Delivery Agent Platform</h1>
          <p className="lede">
            The UI looks like a normal delivery chat. Under the hood, each backend service owns a service-local Arachne agent.
          </p>
        </div>
        <div className="hero-card">
          <span>Current session</span>
          <strong>{sessionId || 'new-session'}</strong>
          <small>Redis keeps the chat session warm while PostgreSQL stores confirmed orders.</small>
        </div>
      </section>

      <section className="workspace">
        <div className="panel chat-panel">
          <div className="panel-header">
            <div>
              <p className="panel-label">Customer Chat</p>
              <h2>Order with natural language</h2>
            </div>
          </div>

          <div className="message-list">
            {conversation.length === 0 ? (
              <div className="message system">
                Ask for something like 2 people, kids friendly, or fastest delivery. The trace panel will show which backend agents responded.
              </div>
            ) : null}

            {conversation.map((entry, index) => (
              <div className={`message ${entry.role === 'assistant' ? 'assistant' : 'user'}`} key={`${entry.role}-${index}`}>
                <span>{entry.role === 'assistant' ? 'Arachne order-agent' : 'Customer'}</span>
                <p>{entry.text}</p>
              </div>
            ))}
          </div>

          <div className="suggestions">
            {suggestions.map((suggestion) => (
              <button key={suggestion} type="button" onClick={() => void sendChat(suggestion)} disabled={loading}>
                {suggestion}
              </button>
            ))}
          </div>

          <form className="composer" onSubmit={onSubmit}>
            <textarea
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="例: 2人分で辛さ控えめ。前回みたいに最速で。"
              rows={4}
            />
            <div className="composer-footer">
              <div>
                {error ? <span className="error">{error}</span> : <span>Chat-first on the surface, multi-agent behind the APIs.</span>}
              </div>
              <button type="submit" disabled={loading || !message.trim()}>
                {loading ? 'Thinking...' : 'Send'}
              </button>
            </div>
          </form>
        </div>

        <div className="sidebar">
          <div className="panel draft-panel">
            <div className="panel-header compact">
              <div>
                <p className="panel-label">Live Draft</p>
                <h2>{draft.status}</h2>
              </div>
              <span className="badge">{totalItems} items</span>
            </div>
            <ul className="draft-list">
              {draft.items.length === 0 ? <li>No items yet.</li> : null}
              {draft.items.map((item) => (
                <li key={`${item.name}-${item.note}`}>
                  <div>
                    <strong>{item.quantity}x {item.name}</strong>
                    <span>{item.note}</span>
                  </div>
                  <em>¥{(item.unitPrice * item.quantity).toFixed(0)}</em>
                </li>
              ))}
            </ul>
            <div className="draft-summary">
              <div><span>ETA</span><strong>{draft.etaLabel || 'Pending'}</strong></div>
              <div><span>Subtotal</span><strong>¥{draft.subtotal.toFixed(0)}</strong></div>
              <div><span>Total</span><strong>¥{draft.total.toFixed(0)}</strong></div>
              <div><span>Payment</span><strong>{draft.paymentStatus} {draft.paymentMethod ? `• ${draft.paymentMethod}` : ''}</strong></div>
              {draft.orderId ? <div><span>Order ID</span><strong>{draft.orderId}</strong></div> : null}
            </div>
          </div>

          <div className="panel trace-panel">
            <div className="panel-header compact">
              <div>
                <p className="panel-label">Service Mesh Trace</p>
                <h2>Hidden agents</h2>
              </div>
            </div>
            <div className="trace-list">
              {trace.length === 0 ? <p>No trace yet.</p> : null}
              {trace.map((entry) => (
                <article key={`${entry.service}-${entry.headline}`} className="trace-card">
                  <div className="trace-meta">
                    <span>{entry.service}</span>
                    <strong>{entry.agent}</strong>
                  </div>
                  <h3>{entry.headline}</h3>
                  <p>{entry.detail}</p>
                </article>
              ))}
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}