'use client';

import { FormEvent, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import ReactMarkdown from 'react-markdown';

import { fetchAuthSession } from '../../lib/browser-session';
import AppNav from '../components/app-nav';

type CampaignSummary = {
  campaignId: string;
  title: string;
  description: string;
  badge: string;
  validUntil: string;
};

type ServiceHealthSummary = {
  serviceName: string;
  status: string;
  healthEndpoint: string;
};

type FaqEntry = {
  id: string;
  question: string;
  answer: string;
  tags: string[];
};

type SupportChatResponse = {
  sessionId: string;
  service: string;
  agent: string;
  headline: string;
  summary: string;
  faqMatches: FaqEntry[];
  campaigns: CampaignSummary[];
  serviceStatuses: ServiceHealthSummary[];
  recentOrders: unknown[];
  relatedFeedback: unknown[];
  handoffTarget: string | null;
  handoffMessage: string | null;
};

type SupportStatusResponse = {
  service: string;
  agent: string;
  summary: string;
  services: ServiceHealthSummary[];
};

type ChatMessage = {
  role: 'user' | 'assistant';
  text: string;
  handoffTarget?: string | null;
  handoffMessage?: string | null;
};

const QUICK_SUGGESTIONS = [
  '現在のキャンペーンを教えて',
  'サービスの稼働状況は？',
  'よくある質問を見たい',
  '注文に問題があります',
];

export default function SupportPage() {
  const router = useRouter();
  const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
  const [serviceStatuses, setServiceStatuses] = useState<ServiceHealthSummary[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sessionId, setSessionId] = useState('');
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  useEffect(() => {
    void fetchAuthSession().then(auth => {
      if (!auth.authenticated) {
        router.replace('/');
        return;
      }

      void Promise.allSettled([
        fetch('/api/support/campaigns')
        .then(r => (r.ok ? (r.json() as Promise<CampaignSummary[]>) : []))
        .then(data => setCampaigns(data as CampaignSummary[]))
        .catch(() => {}),
        fetch('/api/support/status')
        .then(r => (r.ok ? (r.json() as Promise<SupportStatusResponse>) : null))
        .then(data => {
          if (data) setServiceStatuses((data as SupportStatusResponse).services ?? []);
        })
        .catch(() => {}),
      ]);
    });
  }, [router]);

  async function sendChat(text: string) {
    if (!text.trim() || loading) return;
    setMessages(prev => [...prev, { role: 'user', text }]);
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/support/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text }),
      });
      if (res.status === 401) {
        router.replace('/');
        return;
      }
      if (!res.ok) throw new Error(`chat failed: ${res.status}`);
      const payload: SupportChatResponse = await res.json();
      if (payload.sessionId) {
        setSessionId(payload.sessionId);
      }
      setMessages(prev => [
        ...prev,
        {
          role: 'assistant',
          text: payload.summary ?? payload.headline ?? '',
          handoffTarget: payload.handoffTarget,
          handoffMessage: payload.handoffMessage,
        },
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'chat failed');
    } finally {
      setLoading(false);
      textareaRef.current?.focus();
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text) return;
    setInput('');
    void sendChat(text);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const text = input.trim();
      if (!text) return;
      setInput('');
      void sendChat(text);
    }
  }

  return (
    <div className="sp-shell">
      {/* Nav */}
      <AppNav center={<><span aria-hidden="true">🎧</span><span className="h-nav-center-title">サポートセンター</span></>} />

      <div className="sp-workspace">
        {/* Sidebar */}
        <aside className="sp-sidebar">
          {/* Campaigns */}
          <div className="sp-card">
            <p className="sp-card-label">🎁 進行中のキャンペーン</p>
            {campaigns.length === 0 ? (
              <p className="sp-card-empty">読み込み中...</p>
            ) : (
              <div className="sp-campaign-list">
                {campaigns.map(c => (
                  <div key={c.campaignId} className="sp-campaign">
                    <div className="sp-campaign-head">
                      <span className="sp-campaign-badge">{c.badge}</span>
                      <span className="sp-campaign-title">{c.title}</span>
                    </div>
                    <p className="sp-campaign-desc">{c.description}</p>
                    <p className="sp-campaign-until">有効期限: {c.validUntil}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Service status */}
          <div className="sp-card">
            <p className="sp-card-label">📡 サービス稼働状況</p>
            {serviceStatuses.length === 0 ? (
              <p className="sp-card-empty">取得中...</p>
            ) : (
              <div className="sp-status-list">
                {serviceStatuses.map(s => (
                  <div key={s.serviceName} className="sp-status-row">
                    <span className={`sp-status-dot sp-status-dot--${s.status.toLowerCase()}`} />
                    <span className="sp-status-name">{s.serviceName}</span>
                    <span className={`sp-status-label sp-status-label--${s.status.toLowerCase()}`}>
                      {s.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </aside>

        {/* Chat panel */}
        <section className="sp-chat">
          <div className="sp-chat-messages">
            {messages.length === 0 && (
              <div className="sp-welcome">
                <p className="sp-welcome-icon">🎧</p>
                <p className="sp-welcome-title">サポートエージェントへようこそ</p>
                <p className="sp-welcome-desc">
                  キャンペーン・FAQ・稼働状況の確認、注文に関するご相談をお気軽にどうぞ。
                </p>
                <div className="sp-suggestions">
                  {QUICK_SUGGESTIONS.map(s => (
                    <button
                      key={s}
                      type="button"
                      className="sp-suggestion"
                      onClick={() => setInput(s)}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((msg, i) => (
              <div key={i} className={`sp-message sp-message--${msg.role}`}>
                <span className="sp-message-role">
                  {msg.role === 'user' ? 'あなた' : 'support-agent'}
                </span>
                {msg.role === 'assistant' ? (
                  <div className="sp-message-md">
                    <ReactMarkdown>{msg.text}</ReactMarkdown>
                  </div>
                ) : (
                  <p>{msg.text}</p>
                )}
                {msg.handoffTarget === 'order' && (
                  <div className="sp-handoff">
                    <span className="sp-handoff-text">
                      {msg.handoffMessage ?? '注文に関するご相談は注文チャットへ。'}
                    </span>
                    <Link href="/order" className="sp-handoff-link">
                      注文チャットへ →
                    </Link>
                  </div>
                )}
              </div>
            ))}

            {loading && (
              <div className="sp-message sp-message--assistant">
                <span className="sp-message-role">support-agent</span>
                <div className="sp-typing">
                  <span className="sp-typing-dot" />
                  <span className="sp-typing-dot" />
                  <span className="sp-typing-dot" />
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {error && <p className="sp-error">{error}</p>}

          <form className="sp-input-row" onSubmit={handleSubmit}>
            <textarea
              ref={textareaRef}
              className="sp-input"
              rows={2}
              placeholder="メッセージを入力… (Shift+Enter で改行)"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
            />
            <button
              type="submit"
              className="sp-send-btn"
              disabled={loading || !input.trim()}
            >
              送信
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
