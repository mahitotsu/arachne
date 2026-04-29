'use client';

import { useEffect, useState } from 'react';

import type { ExecutionHistoryResponse, HistoryEvent } from '../api/execution-history/route';

// ── Service colour mapping ────────────────────────────────────────────────────

const SERVICE_COLOURS: Record<string, string> = {
  'order-service':   'var(--accent)',
  'menu-service':    'var(--green)',
  'delivery-service':'var(--p-blue-t)',
  'payment-service': 'var(--p-teal-t)',
  'support-service': 'var(--p-purple-t)',
};

function serviceColour(service: string): string {
  return SERVICE_COLOURS[service] ?? 'var(--ink-muted)';
}

// ── Category label ────────────────────────────────────────────────────────────

function categoryLabel(category: string): string {
  switch (category) {
    case 'workflow':    return 'workflow';
    case 'downstream':  return 'downstream';
    case 'agent':       return 'agent';
    case 'model':       return 'model';
    case 'tool':        return 'tool';
    case 'service':     return 'service';
    default:            return category;
  }
}

// ── Duration label ────────────────────────────────────────────────────────────

function durationLabel(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

// ── Time label ────────────────────────────────────────────────────────────────

function timeLabel(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch {
    return iso;
  }
}

// ── Single event row ──────────────────────────────────────────────────────────

function EventRow({ event, index }: { event: HistoryEvent; index: number }) {
  const [expanded, setExpanded] = useState(false);
  const colour = serviceColour(event.service);
  const isLast = false; // line is always drawn below; panel parent clips it

  return (
    <div className="eh-event">
      {/* Timeline spine */}
      <div className="eh-event-spine">
        <div className="eh-event-dot" style={{ background: colour }} />
        <div className="eh-event-line" />
      </div>

      {/* Content */}
      <div className="eh-event-body">
        <div className="eh-event-header">
          <span className="eh-event-service" style={{ color: colour }}>
            {event.service}
          </span>
          <span className={`eh-event-category eh-event-category--${event.category}`}>
            {categoryLabel(event.category)}
          </span>
          <span className={`eh-event-outcome eh-event-outcome--${event.outcome}`}>
            {event.outcome}
          </span>
          <span className="eh-event-duration">{durationLabel(event.durationMs)}</span>
          <span className="eh-event-time">{timeLabel(event.occurredAt)}</span>
        </div>

        <p className="eh-event-headline">{event.headline}</p>

        {event.detail && (
          <button
            type="button"
            className="eh-expand-btn"
            onClick={() => setExpanded(v => !v)}
          >
            {expanded ? '▲ 詳細を閉じる' : '▼ 詳細を見る'}
          </button>
        )}

        {expanded && event.detail && (
          <pre className="eh-event-detail">{event.detail}</pre>
        )}

        {event.skills && event.skills.length > 0 && (
          <div className="eh-event-skills">
            {event.skills.map(s => (
              <span key={s} className="eh-skill-chip">{s}</span>
            ))}
          </div>
        )}

        {event.usage && (event.usage.inputTokens > 0 || event.usage.outputTokens > 0) && (
          <p className="eh-event-usage">
            in {event.usage.inputTokens} / out {event.usage.outputTokens}
            {event.usage.cacheReadTokens > 0 && ` / cache-read ${event.usage.cacheReadTokens}`}
            {' '}tokens
          </p>
        )}
      </div>
    </div>
  );
}

// ── Main panel ────────────────────────────────────────────────────────────────

type Props = {
  /** When this value changes the cached events are cleared and re-fetched on next open. */
  refreshKey?: string | number;
};

export default function ExecutionHistoryPanel({ refreshKey }: Props) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [events, setEvents] = useState<HistoryEvent[] | null>(null);
  const [error, setError] = useState('');

  // Clear cached events whenever refreshKey changes (e.g. step advances).
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    setEvents(null);
    setError('');
  }, [refreshKey]);

  function doFetch() {
    setLoading(true);
    setError('');
    fetch('/api/execution-history', { cache: 'no-store' })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<ExecutionHistoryResponse>;
      })
      .then(data => {
        setEvents(data.events);
      })
      .catch(e => {
        setError(e instanceof Error ? e.message : '実行履歴の取得に失敗しました');
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    if (!open || events !== null || loading) return;
    doFetch();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, events]);

  return (
    <div className="eh-panel">
      <button
        type="button"
        className="eh-toggle"
        onClick={() => setOpen(v => !v)}
      >
        <span className="eh-toggle-icon">{open ? '▲' : '▼'}</span>
        🔍 実行履歴を見る
        {events !== null && !open && (
          <span className="eh-toggle-count">{events.length} イベント</span>
        )}
      </button>

      {open && (
        <div className="eh-body">
          <div className="eh-body-toolbar">
            <button
              type="button"
              className="eh-refresh-btn"
              disabled={loading}
              onClick={() => { setEvents(null); setError(''); }}
            >
              {loading ? '読み込み中…' : '↺ 更新'}
            </button>
          </div>
          {loading && <p className="eh-loading">読み込み中…</p>}
          {error && <p className="eh-error">⚠ {error}</p>}
          {!loading && !error && events !== null && events.length === 0 && (
            <p className="eh-empty">このステップにまだ実行履歴はありません。</p>
          )}
          {!loading && !error && events !== null && events.length > 0 && (
            <div className="eh-timeline">
              {events.map((ev, i) => (
                <EventRow key={`${ev.service}-${ev.sequence}`} event={ev} index={i} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
