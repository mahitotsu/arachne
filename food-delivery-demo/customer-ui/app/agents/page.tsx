'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import ReactMarkdown from 'react-markdown';

import { fetchAuthSession } from '../../lib/browser-session';
import AppNav from '../components/app-nav';
import PageHeader from '../components/page-header';
import AppFooter from '../components/app-footer';

type ToolPayload = {
  name: string;
  content: string;
};

type AgentServiceDescriptor = {
  serviceName: string;
  endpoint: string;
  capability: string;
  agentName: string;
  systemPrompt: string;
  skills: ToolPayload[];
  tools: ToolPayload[];
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

const AGENT_ICONS: Record<string, string> = {
  'delivery-agent':          '🚚',
  'menu-agent':              '🍱',
  'kitchen-agent':           '🍳',
  'support-agent':           '🎧',
  'capability-registry-agent': '🗂️',
};

function agentIcon(agentName: string): string {
  return AGENT_ICONS[agentName] ?? '🤖';
}

function parseSkillContent(content: string): string {
  if (!content.startsWith('---')) return content;
  const end = content.indexOf('---', 3);
  return end === -1 ? content : content.slice(end + 3).trim();
}

export default function AgentsPage() {
  const router = useRouter();
  const [agents, setAgents] = useState<AgentServiceDescriptor[]>([]);
  const [healthMap, setHealthMap] = useState<Record<string, 'AVAILABLE' | 'NOT_AVAILABLE'>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<AgentServiceDescriptor | null>(null);
  const [expandedSkills, setExpandedSkills] = useState<Set<string>>(new Set());

  useEffect(() => {
    void fetchAuthSession().then(auth => {
      if (!auth.authenticated) {
        router.replace('/');
        return;
      }

      void Promise.allSettled([
        fetch('/api/registry/services')
          .then(r => (r.ok ? (r.json() as Promise<AgentServiceDescriptor[]>) : []))
          .then(data => {
            const aiAgents = (data as AgentServiceDescriptor[]).filter(
              s => Array.isArray(s.tools) && s.tools.length > 0,
            );
            setAgents(aiAgents);
          })
          .catch(() => setError('エージェント一覧の取得に失敗しました')),
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
    });
  }, [router]);

  function statusOf(serviceName: string): 'AVAILABLE' | 'NOT_AVAILABLE' {
    return healthMap[serviceName] ?? 'NOT_AVAILABLE';
  }

  return (
    <div className="ag-shell">
      {/* Nav */}
      <AppNav />

      {/* Header */}
      <PageHeader
        icon="🤖"
        title="AI エージェント一覧"
        lead="このシステムで稼働中の Arachne AI エージェントです。各エージェントのシステムプロンプト・利用ツール・スキルを確認できます。"
      />

      {/* Content */}
      {loading ? (
        <div className="ag-loading">読み込み中…</div>
      ) : error ? (
        <div className="ag-error">{error}</div>
      ) : (
        <div className="ag-grid">
          {agents.map(agent => {
            const isUp = statusOf(agent.serviceName) === 'AVAILABLE';
            return (
              <button
                key={agent.serviceName}
                type="button"
                className="ag-card"
                onClick={() => { setSelected(agent); setExpandedSkills(new Set()); }}
              >
                <div className="ag-card-icon" aria-hidden="true">{agentIcon(agent.agentName)}</div>
                <div className="ag-card-header">
                  <span className="ag-card-name">{agent.agentName}</span>
                  <span className={`ag-badge ag-badge--${isUp ? 'up' : 'down'}`}>
                    {isUp ? 'UP' : 'DOWN'}
                  </span>
                </div>
                <p className="ag-card-service">via {agent.serviceName}</p>
                <p className="ag-card-capability">{agent.capability}</p>
                <div className="ag-card-chips">
                  {agent.tools.slice(0, 3).map(t => (
                    <span key={t.name} className="ag-tool-chip">{t.name}</span>
                  ))}
                  {agent.tools.length > 3 && (
                    <span className="ag-tool-chip ag-tool-chip--more">+{agent.tools.length - 3}</span>
                  )}
                  {agent.skills.length > 0 && agent.skills.slice(0, 2).map(s => (
                    <span key={s.name} className="ag-skill-chip">{s.name}</span>
                  ))}
                </div>
                <span className="ag-card-detail-hint">詳細を見る →</span>
              </button>
            );
          })}
        </div>
      )}

      {/* Detail modal */}
      {selected && (
        <div
          className="ag-modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={`${selected.agentName} の詳細`}
          onClick={e => { if (e.target === e.currentTarget) setSelected(null); }}
        >
          <div className="ag-modal">
            <div className="ag-modal-titlebar">
              <div className="ag-modal-title-group">
                <span className="ag-modal-icon" aria-hidden="true">{agentIcon(selected.agentName)}</span>
                <div>
                  <div className="ag-modal-service">{selected.agentName}</div>
                  <div className="ag-modal-sub">via {selected.serviceName}</div>
                </div>
                <span className={`ag-badge ag-badge--${statusOf(selected.serviceName) === 'AVAILABLE' ? 'up' : 'down'}`}>
                  {statusOf(selected.serviceName) === 'AVAILABLE' ? 'UP' : 'DOWN'}
                </span>
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
              {/* Endpoint */}
              <div className="ag-modal-section">
                <span className="ag-modal-section-title">エンドポイント</span>
                <span className="ag-modal-endpoint">
                  <span className="ag-modal-method">{selected.requestMethod}</span>
                  <span>{selected.requestPath}</span>
                </span>
              </div>

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

              {/* Tools */}
              {selected.tools.length > 0 && (
                <div className="ag-modal-section">
                  <span className="ag-modal-section-title">利用ツール ({selected.tools.length})</span>
                  <div className="ag-tool-list">
                    {selected.tools.map(tool => (
                      <div key={tool.name} className="ag-tool-row">
                        <code className="ag-tool-name">{tool.name}</code>
                        <span className="ag-tool-desc">{tool.content}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Skills */}
              {selected.skills.length > 0 && (
                <div className="ag-modal-section">
                  <span className="ag-modal-section-title">スキル ({selected.skills.length})</span>
                  <div className="ag-skill-list">
                    {selected.skills.map(sk => (
                      <div key={sk.name} className="ag-skill-item">
                        <div
                          className="ag-skill-header"
                          onClick={() => setExpandedSkills(prev => {
                            const next = new Set(prev);
                            if (next.has(sk.name)) next.delete(sk.name); else next.add(sk.name);
                            return next;
                          })}
                        >
                          <span className="ag-skill-name">{sk.name}</span>
                          <span className="ag-skill-toggle">{expandedSkills.has(sk.name) ? '▲' : '▼'}</span>
                        </div>
                        {expandedSkills.has(sk.name) && (
                          <div className="ag-skill-body">
                            <div className="ag-skill-body-md">
                              <ReactMarkdown>{parseSkillContent(sk.content)}</ReactMarkdown>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <AppFooter />
    </div>
  );
}
