'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

type SkillPayload = {
  name: string;
  content: string;
};

type AgentServiceDescriptor = {
  serviceName: string;
  endpoint: string;
  capability: string;
  agentName: string;
  systemPrompt: string;
  skills: SkillPayload[];
  requestMethod: string;
  requestPath: string;
  status: 'AVAILABLE' | 'NOT_AVAILABLE';
};

type HealthEntry = {
  serviceName: string;
  status: 'AVAILABLE' | 'NOT_AVAILABLE';
  healthEndpoint: string;
};

type HealthResponse = {
  services: HealthEntry[];
};

const ACCESS_TOKEN_KEY = 'delivery-demo-access-token';

export default function AgentsPage() {
  const router = useRouter();
  const [services, setServices] = useState<AgentServiceDescriptor[]>([]);
  const [healthMap, setHealthMap] = useState<Record<string, 'AVAILABLE' | 'NOT_AVAILABLE'>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<AgentServiceDescriptor | null>(null);

  useEffect(() => {
    const token = window.localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) {
      router.replace('/');
      return;
    }

    void Promise.allSettled([
      fetch('/api/registry/services')
        .then(r => (r.ok ? (r.json() as Promise<AgentServiceDescriptor[]>) : []))
        .then(data => setServices(data as AgentServiceDescriptor[]))
        .catch(() => setError('サービス一覧の取得に失敗しました')),
      fetch('/api/registry/health')
        .then(r => (r.ok ? (r.json() as Promise<HealthResponse>) : null))
        .then(data => {
          if (!data) return;
          const map: Record<string, 'AVAILABLE' | 'NOT_AVAILABLE'> = {};
          for (const entry of (data as HealthResponse).services ?? []) {
            map[entry.serviceName] = entry.status;
          }
          setHealthMap(map);
        })
        .catch(() => {}),
    ]).finally(() => setLoading(false));
  }, [router]);

  function statusOf(serviceName: string): 'AVAILABLE' | 'NOT_AVAILABLE' {
    return healthMap[serviceName] ?? 'NOT_AVAILABLE';
  }

  return (
    <div className="ag-shell">
      {/* Nav */}
      <nav className="h-nav">
        <Link href="/home" className="h-nav-brand">
          <span>🍜</span>
          <span className="h-nav-name">Arachne Kitchen</span>
        </Link>
        <div className="h-nav-right">
          <Link href="/home" className="ag-nav-back">
            ← ホームへ
          </Link>
        </div>
      </nav>

      {/* Header */}
      <header className="ag-header">
        <h1 className="ag-header-title">エージェント仕様ビューワー</h1>
        <p className="ag-header-lead">
          registry-service に登録された全エージェントのケイパビリティ・プロンプト・スキルを確認できます。
        </p>
      </header>

      {/* Content */}
      {loading ? (
        <div className="ag-loading">読み込み中…</div>
      ) : error ? (
        <div className="ag-error">{error}</div>
      ) : (
        <div className="ag-grid">
          {services.map(svc => {
            const live = statusOf(svc.serviceName);
            const isUp = live === 'AVAILABLE';
            return (
              <button
                key={svc.serviceName}
                type="button"
                className="ag-card"
                onClick={() => setSelected(svc)}
              >
                <div className="ag-card-header">
                  <span className="ag-card-name">{svc.serviceName}</span>
                  <span className={`ag-badge ag-badge--${isUp ? 'up' : 'down'}`}>
                    {isUp ? '● UP' : '○ DOWN'}
                  </span>
                </div>
                <div className="ag-card-agent">{svc.agentName}</div>
                <p className="ag-card-capability">{svc.capability}</p>
                {svc.skills.length > 0 && (
                  <div className="ag-card-skills">
                    {svc.skills.map(sk => (
                      <span key={sk.name} className="ag-skill-chip">{sk.name}</span>
                    ))}
                  </div>
                )}
                <span className="ag-card-detail-hint">詳細を見る →</span>
              </button>
            );
          })}
        </div>
      )}

      {/* Modal */}
      {selected && (
        <div
          className="ag-modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={`${selected.serviceName} の詳細`}
          onClick={e => { if (e.target === e.currentTarget) setSelected(null); }}
        >
          <div className="ag-modal">
            <div className="ag-modal-titlebar">
              <div>
                <span className="ag-modal-service">{selected.serviceName}</span>
                <span className="ag-modal-agent"> / {selected.agentName}</span>
              </div>
              <button
                type="button"
                className="ag-modal-close"
                aria-label="閉じる"
                onClick={() => setSelected(null)}
              >
                ✕
              </button>
            </div>

            <div className="ag-modal-body">
              {/* Status & endpoint */}
              <div className="ag-modal-row">
                <span className="ag-modal-label">稼働状態</span>
                <span className={`ag-badge ag-badge--${statusOf(selected.serviceName) === 'AVAILABLE' ? 'up' : 'down'}`}>
                  {statusOf(selected.serviceName) === 'AVAILABLE' ? '● UP' : '○ DOWN'}
                </span>
              </div>
              {selected.endpoint && (
                <div className="ag-modal-row">
                  <span className="ag-modal-label">エンドポイント</span>
                  <span className="ag-modal-value ag-mono">{selected.endpoint}{selected.requestPath}</span>
                </div>
              )}

              {/* Capability */}
              <div className="ag-modal-section">
                <span className="ag-modal-section-title">ケイパビリティ</span>
                <p className="ag-modal-text">{selected.capability}</p>
              </div>

              {/* System prompt */}
              {selected.systemPrompt && (
                <div className="ag-modal-section">
                  <span className="ag-modal-section-title">システムプロンプト</span>
                  <pre className="ag-modal-pre">{selected.systemPrompt}</pre>
                </div>
              )}

              {/* Skills */}
              {selected.skills.length > 0 && (
                <div className="ag-modal-section">
                  <span className="ag-modal-section-title">スキル ({selected.skills.length})</span>
                  <div className="ag-skill-list">
                    {selected.skills.map(sk => (
                      <details key={sk.name} className="ag-skill-detail">
                        <summary className="ag-skill-summary">{sk.name}</summary>
                        <pre className="ag-skill-content">{sk.content}</pre>
                      </details>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
