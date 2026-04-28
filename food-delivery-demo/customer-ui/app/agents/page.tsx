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

type OpenApiInputField = { field: string; meaning: string };
type OpenApiPromptContract = {
  agent?: string;
  contract?: {
    requiredInputs?: OpenApiInputField[];
    optionalInputs?: OpenApiInputField[];
    serviceBehavior?: string;
  };
};
type OpenApiOperation = {
  summary?: string;
  description?: string;
  'x-ai-prompt-contract'?: OpenApiPromptContract;
};
type OpenApiSpec = {
  info?: { title?: string; description?: string };
  paths?: Record<string, Record<string, OpenApiOperation>>;
};

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'] as const;
function getApiOperations(spec: OpenApiSpec): Array<{ method: string; path: string; op: OpenApiOperation }> {
  const result: Array<{ method: string; path: string; op: OpenApiOperation }> = [];
  for (const [path, pathItem] of Object.entries(spec.paths ?? {})) {
    for (const method of HTTP_METHODS) {
      const op = pathItem[method];
      if (op) result.push({ method, path, op });
    }
  }
  return result;
}

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
  const [modalTab, setModalTab] = useState<'overview' | 'tools' | 'api'>('overview');
  const [openApiCache, setOpenApiCache] = useState<Record<string, OpenApiSpec>>({});
  const [loadingApi, setLoadingApi] = useState(false);

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

  function loadApiSpec(svcName: string) {
    if (openApiCache[svcName] || loadingApi) return;
    setLoadingApi(true);
    void fetch(`/api/openapi/${encodeURIComponent(svcName)}`)
      .then(r => (r.ok ? (r.json() as Promise<OpenApiSpec>) : null))
      .then(spec => { if (spec) setOpenApiCache(prev => ({ ...prev, [svcName]: spec })); })
      .catch(() => {})
      .finally(() => setLoadingApi(false));
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

      <div className="ag-content">

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
                onClick={() => { setSelected(agent); setExpandedSkills(new Set()); setModalTab('overview'); }}
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
                  {agent.skills.length > 0
                    ? agent.skills.slice(0, 2).map(s => (
                        <span key={s.name} className="ag-skill-chip">{s.name}</span>
                      ))
                    : <span className="ag-skill-chip ag-skill-chip--none">スキルなし</span>
                  }
                  <span className="ag-api-chip">API仕様</span>
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

            {/* Modal tabs */}
            <div className="ag-modal-tabs">
              <button type="button" className={`ag-modal-tab${modalTab === 'overview' ? ' ag-modal-tab--active' : ''}`} onClick={() => setModalTab('overview')}>概要</button>
              <button type="button" className={`ag-modal-tab${modalTab === 'tools' ? ' ag-modal-tab--active' : ''}`} onClick={() => setModalTab('tools')}>ツール・スキル</button>
              <button type="button" className={`ag-modal-tab${modalTab === 'api' ? ' ag-modal-tab--active' : ''}`} onClick={() => { setModalTab('api'); loadApiSpec(selected.serviceName); }}>API 仕様</button>
            </div>

            <div className="ag-modal-body">

              {/* Tab: 概要 */}
              {modalTab === 'overview' && (
                <>
                  <div className="ag-modal-section">
                    <span className="ag-modal-section-title">エンドポイント</span>
                    <span className="ag-modal-endpoint">
                      <span className="ag-modal-method">{selected.requestMethod}</span>
                      <span>{selected.requestPath}</span>
                    </span>
                  </div>
                  <div className="ag-modal-section">
                    <span className="ag-modal-section-title">ケイパビリティ</span>
                    <p className="ag-modal-text">{selected.capability}</p>
                  </div>
                  {selected.systemPrompt && (
                    <div className="ag-modal-section">
                      <span className="ag-modal-section-title">システムプロンプト</span>
                      <pre className="ag-modal-pre">{selected.systemPrompt}</pre>
                    </div>
                  )}
                </>
              )}

              {/* Tab: ツール・スキル */}
              {modalTab === 'tools' && (
                <>
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
                  <div className="ag-modal-section">
                    <span className="ag-modal-section-title">スキル ({selected.skills.length})</span>
                    {selected.skills.length === 0 ? (
                      <p className="ag-modal-no-skills">スキル定義がありません</p>
                    ) : (
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
                    )}
                  </div>
                </>
              )}

              {/* Tab: API 仕様 */}
              {modalTab === 'api' && (
                loadingApi
                  ? <p className="ag-api-loading">読み込み中…</p>
                  : !openApiCache[selected.serviceName]
                    ? <p className="ag-api-loading">API仕様を取得できませんでした</p>
                    : (
                      <div className="ag-api-list">
                        {getApiOperations(openApiCache[selected.serviceName]).map(({ method, path, op }) => {
                          const contract = op['x-ai-prompt-contract'];
                          return (
                            <div key={`${method}:${path}`} className="ag-api-op">
                              <div className="ag-api-op-header">
                                <span className={`ag-api-op-badge ag-api-op-badge--${method}`}>{method.toUpperCase()}</span>
                                <div className="ag-api-op-meta">
                                  <span className="ag-api-op-path">{path}</span>
                                  {op.summary && <span className="ag-api-op-summary">{op.summary}</span>}
                                </div>
                              </div>
                              {(op.description || contract?.contract) && (
                                <div className="ag-api-op-body">
                                  {op.description && <p className="ag-api-op-desc">{op.description}</p>}
                                  {contract?.contract && (
                                    <div className="ag-contract-block">
                                      {contract.agent && (
                                        <p className="ag-contract-agent">担当エージェント: <strong>{contract.agent}</strong></p>
                                      )}
                                      {(contract.contract.requiredInputs?.length ?? 0) > 0 && (
                                        <>
                                          <p className="ag-contract-section-label">必須入力</p>
                                          <ul className="ag-contract-fields">
                                            {contract.contract.requiredInputs!.map(f => (
                                              <li key={f.field}><code className="ag-contract-field-name">{f.field}</code>{f.meaning}</li>
                                            ))}
                                          </ul>
                                        </>
                                      )}
                                      {(contract.contract.optionalInputs?.length ?? 0) > 0 && (
                                        <>
                                          <p className="ag-contract-section-label">任意入力</p>
                                          <ul className="ag-contract-fields">
                                            {contract.contract.optionalInputs!.map(f => (
                                              <li key={f.field}><code className="ag-contract-field-name">{f.field}</code>{f.meaning}</li>
                                            ))}
                                          </ul>
                                        </>
                                      )}
                                      {contract.contract.serviceBehavior && (
                                        <>
                                          <p className="ag-contract-section-label">サービス動作</p>
                                          <p className="ag-contract-behavior">{contract.contract.serviceBehavior}</p>
                                        </>
                                      )}
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )
              )}

            </div>
          </div>
        </div>
      )}

      </div>{/* ag-content */}

      <AppFooter />
    </div>
  );
}
