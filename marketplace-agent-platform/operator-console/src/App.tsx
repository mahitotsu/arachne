import { FormEvent, startTransition, useEffect, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import {
  ActivityEvent,
  ApprovalSubmissionResult,
  CaseDetailView,
  CaseListItem,
  CaseStatus,
  CaseType,
} from './types';

const API_BASE_URL = (import.meta.env.VITE_CASE_SERVICE_BASE_URL as string | undefined) ?? '';

const CASE_TYPES: CaseType[] = [
  'ITEM_NOT_RECEIVED',
  'DELIVERED_BUT_DAMAGED',
  'HIGH_RISK_SETTLEMENT_HOLD',
  'SELLER_CANCELLATION_AFTER_AUTHORIZATION',
];

const CASE_STATUS_FILTERS: CaseStatus[] = [
  'OPEN',
  'GATHERING_EVIDENCE',
  'AWAITING_APPROVAL',
  'READY_FOR_SETTLEMENT',
  'COMPLETED',
];

type ScenarioPreset = {
  id: string;
  label: string;
  caseType: CaseType;
  orderId: string;
  amount: string;
  currency: string;
  initialMessage: string;
};

const SCENARIO_PRESETS: ScenarioPreset[] = [
  {
    id: 'item-not-received-hold',
    label: 'Item Not Received: high-value hold',
    caseType: 'ITEM_NOT_RECEIVED',
    orderId: 'order-1001',
    amount: '149.95',
    currency: 'USD',
    initialMessage: 'Buyer reports item not received and asks why the shipment has stalled in transit.',
  },
  {
    id: 'item-not-received-refund',
    label: 'Item Not Received: low-value refund path',
    caseType: 'ITEM_NOT_RECEIVED',
    orderId: 'order-1049',
    amount: '49.95',
    currency: 'USD',
    initialMessage: 'Buyer reports item not received, no carrier delivery scan is visible, and asks for a refund review.',
  },
  {
    id: 'delivered-damaged',
    label: 'Delivered But Damaged: gather more evidence',
    caseType: 'DELIVERED_BUT_DAMAGED',
    orderId: 'order-dmg-2007',
    amount: '189.00',
    currency: 'USD',
    initialMessage: 'Buyer says the package was delivered crushed and wet and asks what evidence is needed to resolve the damage claim.',
  },
  {
    id: 'high-risk-hold',
    label: 'High Risk Settlement Hold: keep funds frozen',
    caseType: 'HIGH_RISK_SETTLEMENT_HOLD',
    orderId: 'order-risk-3012',
    amount: '249.00',
    currency: 'USD',
    initialMessage: 'Risk flagged unusual account activity and the operator needs to know whether funds should stay on hold.',
  },
  {
    id: 'seller-cancelled-refund',
    label: 'Seller Cancellation: refund path',
    caseType: 'SELLER_CANCELLATION_AFTER_AUTHORIZATION',
    orderId: 'order-cancel-4101',
    amount: '129.00',
    currency: 'USD',
    initialMessage: 'Seller cancelled the order after authorization but before carrier handoff and the buyer is asking for a refund.',
  },
];

const DEFAULT_SCENARIO_ID = 'item-not-received-hold';

type ViewMode = 'list' | 'detail';
type InboxFilter = 'all' | 'needs-action' | 'in-flight' | 'completed';
type TimelineKind = 'conversation' | 'system';

type TimelineEntry = {
  id: string;
  actorName: string;
  actorRole: string;
  kind: TimelineKind;
  summary: string;
  body: string;
  timestamp: string;
  emphasis?: string;
  meta?: string[];
};

type ConversationThread = {
  id: string;
  title: string;
  subtitle: string;
  root: TimelineEntry;
  replies: TimelineEntry[];
  systemEntries: TimelineEntry[];
  participants: string[];
  lastTimestamp: string;
};

function App() {
  const apiEndpointLabel = API_BASE_URL || window.location.origin;
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [cases, setCases] = useState<CaseListItem[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState('');
  const [selectedCase, setSelectedCase] = useState<CaseDetailView | null>(null);
  const [searchDraft, setSearchDraft] = useState('');
  const [searchText, setSearchText] = useState('');
  const [caseTypeFilter, setCaseTypeFilter] = useState('');
  const [caseStatusFilter, setCaseStatusFilter] = useState('');
  const [inboxFilter, setInboxFilter] = useState<InboxFilter>('all');
  const [isLoadingCases, setIsLoadingCases] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [isSubmittingMessage, setIsSubmittingMessage] = useState(false);
  const [isSubmittingApproval, setIsSubmittingApproval] = useState(false);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [collapsedThreads, setCollapsedThreads] = useState<Record<string, boolean>>({});
  const [errorMessage, setErrorMessage] = useState('');
  const [flashMessage, setFlashMessage] = useState('');
  const [createScenarioId, setCreateScenarioId] = useState(DEFAULT_SCENARIO_ID);
  const [createForm, setCreateForm] = useState({
    caseType: 'ITEM_NOT_RECEIVED' as CaseType,
    orderId: 'order-1001',
    amount: '149.95',
    currency: 'USD',
    initialMessage: 'Buyer reports item not received and requests an investigation.',
    operatorId: 'operator-1',
    operatorRole: 'CASE_OPERATOR',
  });
  const [messageForm, setMessageForm] = useState({
    message: 'Please explain the latest recommendation in plain language.',
    operatorId: 'operator-1',
    operatorRole: 'CASE_OPERATOR',
  });
  const [approvalForm, setApprovalForm] = useState({
    decision: 'APPROVE',
    comment: 'Approved based on the current evidence and recommendation.',
    actorId: 'finance-1',
    actorRole: 'FINANCE_CONTROL',
  });

  useEffect(() => {
    void loadCases();
  }, [searchText, caseTypeFilter, caseStatusFilter]);

  useEffect(() => {
    if (!selectedCaseId) {
      setSelectedCase(null);
      return;
    }
    void loadCaseDetail(selectedCaseId);
  }, [selectedCaseId]);

  useEffect(() => {
    if (!selectedCaseId || viewMode !== 'detail') {
      return;
    }

    const eventSource = new EventSource(`${API_BASE_URL}/api/cases/${selectedCaseId}/activity-stream`);

    eventSource.addEventListener('activity', (event) => {
      const nextEvent = JSON.parse((event as MessageEvent<string>).data) as ActivityEvent;
      setSelectedCase((current) => {
        if (!current || current.caseId !== selectedCaseId) {
          return current;
        }
        if (current.activityHistory.some((activity) => activity.eventId === nextEvent.eventId)) {
          return current;
        }
        return {
          ...current,
          activityHistory: [...current.activityHistory, nextEvent],
        };
      });
      void loadCases();
      void loadCaseDetail(selectedCaseId, false);
    });

    eventSource.onerror = () => {
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [selectedCaseId, viewMode]);

  const filteredCases = useMemo(() => {
    return cases.filter((item) => matchesInboxFilter(item, inboxFilter));
  }, [cases, inboxFilter]);

  const queueSummary = useMemo(() => buildQueueSummary(cases), [cases]);

  const selectedCaseThreads = useMemo(() => {
    if (!selectedCase) {
      return [] as ConversationThread[];
    }
    return buildConversationThreads(selectedCase.activityHistory, selectedCase);
  }, [selectedCase]);

  useEffect(() => {
    if (!selectedCaseThreads.length) {
      setCollapsedThreads({});
      return;
    }

    setCollapsedThreads((current) => {
      const next: Record<string, boolean> = {};
      for (const thread of selectedCaseThreads) {
        next[thread.id] = current[thread.id] ?? false;
      }
      return next;
    });
  }, [selectedCaseThreads]);

  const agentRoster = useMemo(() => {
    if (!selectedCase) {
      return [] as Array<{ name: string; role: string; status: string }>;
    }
    return buildAgentRoster(selectedCase.activityHistory);
  }, [selectedCase]);

  const aiBrief = useMemo(() => {
    if (!selectedCase) {
      return [] as string[];
    }
    return buildAiBrief(selectedCase);
  }, [selectedCase]);

  async function loadCases(preserveSelection = true) {
    setIsLoadingCases(true);
    try {
      const params = new URLSearchParams();
      if (searchText.trim()) {
        params.set('q', searchText.trim());
      }
      if (caseTypeFilter) {
        params.set('caseType', caseTypeFilter);
      }
      if (caseStatusFilter) {
        params.set('caseStatus', caseStatusFilter);
      }

      const response = await fetch(`${API_BASE_URL}/api/cases${params.size ? `?${params.toString()}` : ''}`);
      if (!response.ok) {
        throw new Error(`Case list request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseListItem[];
      setCases(payload);

      if (!preserveSelection && payload.length > 0) {
        startTransition(() => setSelectedCaseId(payload[0].caseId));
        return;
      }

      if (selectedCaseId && payload.every((item) => item.caseId !== selectedCaseId)) {
        startTransition(() => {
          setSelectedCaseId(payload[0]?.caseId ?? '');
          setViewMode(payload[0] ? 'detail' : 'list');
        });
      }

      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to load cases.');
    } finally {
      setIsLoadingCases(false);
    }
  }

  async function loadCaseDetail(caseId: string, showBusy = true) {
    if (showBusy) {
      setIsLoadingDetail(true);
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/cases/${caseId}`);
      if (!response.ok) {
        throw new Error(`Case detail request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseDetailView;
      setSelectedCase(payload);
      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to load case detail.');
    } finally {
      if (showBusy) {
        setIsLoadingDetail(false);
      }
    }
  }

  async function handleCreateCase(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmittingCreate(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/cases`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...createForm,
          amount: Number(createForm.amount),
        }),
      });

      if (!response.ok) {
        throw new Error(`Create case request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseDetailView;
      setSelectedCase(payload);
      startTransition(() => {
        setSelectedCaseId(payload.caseId);
        setViewMode('detail');
      });
      await loadCases(false);
      setIsCreateModalOpen(false);
      setFlashMessage(`Case ${payload.caseId} created.`);
      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to create case.');
      setFlashMessage('');
    } finally {
      setIsSubmittingCreate(false);
    }
  }

  async function handleAddMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedCaseId) {
      return;
    }

    setIsSubmittingMessage(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/cases/${selectedCaseId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(messageForm),
      });

      if (!response.ok) {
        throw new Error(`Add message request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseDetailView;
      setSelectedCase(payload);
      setFlashMessage(`Sent a new case instruction for ${payload.caseId}.`);
      setErrorMessage('');
      await loadCases();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to add message.');
      setFlashMessage('');
    } finally {
      setIsSubmittingMessage(false);
    }
  }

  async function handleApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedCaseId) {
      return;
    }

    setIsSubmittingApproval(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/cases/${selectedCaseId}/approvals`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(approvalForm),
      });

      if (!response.ok) {
        throw new Error(`Approval request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as ApprovalSubmissionResult;
      setFlashMessage(payload.message);
      setErrorMessage('');
      await loadCaseDetail(selectedCaseId);
      await loadCases();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to submit approval.');
      setFlashMessage('');
    } finally {
      setIsSubmittingApproval(false);
    }
  }

  return (
    <div className="app-shell">
      <header className="hero">
        <div className="hero-copy">
          <p className="kicker">Marketplace Agent Platform</p>
          <h1>Operator Console</h1>
          <p className="hero-text">
            Ticket-style case handling with AI search, agent-visible collaboration, and approval history that reads like an operational workflow instead of a raw trace.
          </p>
        </div>
        <div className="hero-meta">
          <div className="hero-meta-card">
            <span>Console origin</span>
            <strong>{apiEndpointLabel}</strong>
          </div>
          <button type="button" className="primary-button" onClick={() => setIsCreateModalOpen(true)}>
            New case
          </button>
        </div>
      </header>

      {(errorMessage || flashMessage) && (
        <section className="banner-row">
          {errorMessage ? <div className="banner banner-error">{errorMessage}</div> : null}
          {!errorMessage && flashMessage ? <div className="banner banner-success">{flashMessage}</div> : null}
        </section>
      )}

      {viewMode === 'list' ? (
        <main className="dashboard">
          <section className="dashboard-top">
            <div className="insight-card insight-card-strong">
              <span className="insight-label">Needs action</span>
              <strong>{queueSummary.needsAction}</strong>
              <p>Cases blocked on approval or still gathering evidence.</p>
            </div>
            <div className="insight-card">
              <span className="insight-label">In flight</span>
              <strong>{queueSummary.inFlight}</strong>
              <p>Active investigations with agent activity still unfolding.</p>
            </div>
            <div className="insight-card">
              <span className="insight-label">Completed today</span>
              <strong>{queueSummary.completed}</strong>
              <p>Resolved cases with recorded outcomes and audit history.</p>
            </div>
            <div className="insight-card insight-card-accent">
              <span className="insight-label">AI search</span>
              <strong>Natural language queue search</strong>
              <p>Type business intent such as refund hold, shipment evidence, or approval needed.</p>
            </div>
          </section>

          <section className="list-layout">
            <div className="panel panel-listing">
              <div className="section-header">
                <div>
                  <p className="kicker">Case Inbox</p>
                  <h2>Cases</h2>
                </div>
                <span className="section-chip">{isLoadingCases ? 'Refreshing' : `${filteredCases.length} visible`}</span>
              </div>

              <form
                className="search-bar"
                onSubmit={(event) => {
                  event.preventDefault();
                  setSearchText(searchDraft);
                }}
              >
                <input
                  value={searchDraft}
                  onChange={(event) => setSearchDraft(event.target.value)}
                  placeholder="Search cases in natural language: refund approval, shipment proof, policy exception"
                />
                <select value={caseTypeFilter} onChange={(event) => setCaseTypeFilter(event.target.value)}>
                  <option value="">All case types</option>
                  {CASE_TYPES.map((caseType) => (
                    <option key={caseType} value={caseType}>{formatLabel(caseType)}</option>
                  ))}
                </select>
                <select value={caseStatusFilter} onChange={(event) => setCaseStatusFilter(event.target.value)}>
                  <option value="">All statuses</option>
                  {CASE_STATUS_FILTERS.map((caseStatus) => (
                    <option key={caseStatus} value={caseStatus}>{formatLabel(caseStatus)}</option>
                  ))}
                </select>
                <button type="submit" className="primary-button">Search</button>
              </form>

              <div className="segmented-row" role="tablist" aria-label="Inbox views">
                <SegmentButton label="All" current={inboxFilter} target="all" onChange={setInboxFilter} />
                <SegmentButton label="Needs Action" current={inboxFilter} target="needs-action" onChange={setInboxFilter} />
                <SegmentButton label="In Flight" current={inboxFilter} target="in-flight" onChange={setInboxFilter} />
                <SegmentButton label="Completed" current={inboxFilter} target="completed" onChange={setInboxFilter} />
              </div>

              <div className="ticket-table">
                <div className="ticket-table-head">
                  <span>Case</span>
                  <span>Status</span>
                  <span>Recommendation</span>
                  <span>Approval</span>
                  <span>Updated</span>
                </div>
                {filteredCases.map((item) => (
                  <button
                    key={item.caseId}
                    type="button"
                    className="ticket-row"
                    onClick={() => {
                      startTransition(() => {
                        setSelectedCaseId(item.caseId);
                        setViewMode('detail');
                      });
                    }}
                  >
                    <div className="ticket-primary">
                      <strong>{item.caseId}</strong>
                      <span>{formatLabel(item.caseType)}</span>
                      <span>Order {item.orderId}</span>
                    </div>
                    <div><StatusBadge value={item.caseStatus} variant="status" /></div>
                    <div><StatusBadge value={item.currentRecommendation} variant="recommendation" /></div>
                    <div><StatusBadge value={formatApprovalState(item)} variant="approval" /></div>
                    <div className="ticket-updated">
                      <strong>{formatRelativeDate(item.updatedAt)}</strong>
                      <span>{formatCurrency(item.amount, item.currency)}</span>
                    </div>
                  </button>
                ))}
                {!filteredCases.length && !isLoadingCases ? (
                  <div className="empty-panel">No cases match the current filters.</div>
                ) : null}
              </div>
            </div>

            <aside className="panel panel-side">
              <div className="section-header compact-header">
                <div>
                  <p className="kicker">Demo Story</p>
                  <h2>Why this view works</h2>
                </div>
              </div>
              <div className="side-stack">
                <article className="note-card">
                  <h3>Familiar structure</h3>
                  <p>The inbox behaves like a ticketing tool: search, filter, inspect, then act in detail view.</p>
                </article>
                <article className="note-card">
                  <h3>Agent collaboration is visible</h3>
                  <p>In detail view, specialists appear as participants in the activity feed rather than raw runtime events.</p>
                </article>
                <article className="note-card">
                  <h3>AI remains legible</h3>
                  <p>The queue accepts natural-language search and each case has an AI brief that explains the latest state.</p>
                </article>
              </div>
            </aside>
          </section>
        </main>
      ) : (
        <main className="detail-page">
          <div className="detail-topbar">
            <button type="button" className="ghost-button" onClick={() => setViewMode('list')}>
              Back to inbox
            </button>
            {selectedCase ? (
              <div className="detail-title-group">
                <div>
                  <p className="kicker">Case Detail</p>
                  <h2>{selectedCase.caseId}</h2>
                </div>
                <StatusBadge value={selectedCase.caseStatus} variant="status" />
              </div>
            ) : null}
          </div>

          {!selectedCase && !isLoadingDetail ? (
            <div className="empty-panel empty-panel-large">Select a case from the inbox to inspect the workflow.</div>
          ) : null}

          {selectedCase ? (
            <div className="detail-grid">
              <section className="detail-main">
                <div className="ticket-header-card">
                  <div>
                    <p className="kicker">{formatLabel(selectedCase.caseType)}</p>
                    <h3>{buildCaseHeadline(selectedCase)}</h3>
                    <p className="ticket-summary-text">{buildCaseSummary(selectedCase)}</p>
                  </div>
                  <div className="ticket-header-metrics">
                    <Metric label="Recommendation" value={formatLabel(selectedCase.currentRecommendation)} />
                    <Metric label="Amount" value={formatCurrency(selectedCase.amount, selectedCase.currency)} />
                    <Metric label="Approval" value={formatLabel(selectedCase.approvalState.approvalStatus)} />
                  </div>
                </div>

                <div className="panel conversation-panel">
                  <div className="section-header">
                    <div>
                      <p className="kicker">Activity</p>
                      <h2>Comments and history</h2>
                    </div>
                    <span className="section-chip">{selectedCaseThreads.length} threads</span>
                  </div>

                  <div className="conversation-feed">
                    {selectedCaseThreads.map((thread) => (
                      <article key={thread.id} className="thread-card">
                        <div className="thread-header">
                          <div>
                            <p className="kicker">{thread.subtitle}</p>
                            <h3>{thread.title}</h3>
                          </div>
                          <div className="thread-header-meta">
                            <span className="section-chip">{thread.replies.length} replies</span>
                            <time>{formatDate(thread.lastTimestamp)}</time>
                            <button
                              type="button"
                              className="thread-toggle"
                              aria-expanded={!collapsedThreads[thread.id]}
                              onClick={() => {
                                setCollapsedThreads((current) => ({
                                  ...current,
                                  [thread.id]: !current[thread.id],
                                }));
                              }}
                            >
                              {collapsedThreads[thread.id] ? 'Expand thread' : 'Collapse thread'}
                            </button>
                          </div>
                        </div>

                        <div className="thread-root">
                          <ConversationBubble entry={thread.root} tone="root" />
                        </div>

                        {collapsedThreads[thread.id] ? (
                          <div className="thread-collapsed-summary">
                            <span>{thread.replies.length} replies hidden</span>
                            <span>{thread.systemEntries.length} system notes hidden</span>
                            <span>{thread.participants.length} participants</span>
                          </div>
                        ) : (
                          <>
                            {thread.replies.length ? (
                              <div className="thread-replies">
                                {thread.replies.map((entry) => (
                                  <ConversationBubble key={entry.id} entry={entry} tone={entry.kind === 'system' ? 'system' : 'reply'} />
                                ))}
                              </div>
                            ) : (
                              <div className="thread-empty">No replies recorded yet for this request.</div>
                            )}

                            <div className="thread-footer">
                              <div className="meta-pill-row">
                                {thread.participants.map((participant) => (
                                  <span key={`${thread.id}-${participant}`} className="meta-pill">{participant}</span>
                                ))}
                              </div>
                              {thread.systemEntries.length ? (
                                <details className="thread-system-details">
                                  <summary>{thread.systemEntries.length} system notes</summary>
                                  <div className="thread-system-list">
                                    {thread.systemEntries.map((entry) => (
                                      <ConversationBubble key={entry.id} entry={entry} tone="system" compact />
                                    ))}
                                  </div>
                                </details>
                              ) : null}
                            </div>
                          </>
                        )}
                      </article>
                    ))}
                  </div>

                  <form className="composer" onSubmit={handleAddMessage}>
                    <label className="composer-field composer-field-full">
                      <span>Operator message</span>
                      <textarea
                        rows={4}
                        value={messageForm.message}
                        onChange={(event) => setMessageForm((current) => ({ ...current, message: event.target.value }))}
                      />
                    </label>
                    <label className="composer-field">
                      <span>Operator id</span>
                      <input
                        value={messageForm.operatorId}
                        onChange={(event) => setMessageForm((current) => ({ ...current, operatorId: event.target.value }))}
                      />
                    </label>
                    <label className="composer-field">
                      <span>Role</span>
                      <select
                        value={messageForm.operatorRole}
                        onChange={(event) => setMessageForm((current) => ({ ...current, operatorRole: event.target.value }))}
                      >
                        <option value="CASE_OPERATOR">CASE_OPERATOR</option>
                        <option value="FINANCE_CONTROL">FINANCE_CONTROL</option>
                      </select>
                    </label>
                    <button type="submit" className="primary-button" disabled={isSubmittingMessage}>
                      {isSubmittingMessage ? 'Sending...' : 'Send instruction'}
                    </button>
                  </form>
                </div>
              </section>

              <aside className="detail-sidebar">
                <div className="panel side-card">
                  <div className="section-header compact-header">
                    <div>
                      <p className="kicker">AI Brief</p>
                      <h2>What matters now</h2>
                    </div>
                  </div>
                  <ul className="brief-list">
                    {aiBrief.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>

                <div className="panel side-card">
                  <div className="section-header compact-header">
                    <div>
                      <p className="kicker">Participants</p>
                      <h2>Agents on this case</h2>
                    </div>
                  </div>
                  <div className="roster-list">
                    {agentRoster.map((agent) => (
                      <article key={`${agent.name}-${agent.status}`} className="roster-item">
                        <div>
                          <strong>{agent.name}</strong>
                          <span>{agent.role}</span>
                        </div>
                        <StatusBadge value={agent.status} variant="agent" />
                      </article>
                    ))}
                  </div>
                </div>

                <div className="panel side-card">
                  <div className="section-header compact-header">
                    <div>
                      <p className="kicker">Evidence</p>
                      <h2>Case record</h2>
                    </div>
                  </div>
                  <div className="evidence-stack">
                    <EvidenceCard label="Shipment" value={selectedCase.evidence.shipmentEvidence} />
                    <EvidenceCard label="Escrow" value={selectedCase.evidence.escrowEvidence} />
                    <EvidenceCard label="Risk" value={selectedCase.evidence.riskEvidence} />
                    <EvidenceCard label="Policy" value={selectedCase.evidence.policyReference} />
                  </div>
                </div>

                <div className="panel side-card">
                  <div className="section-header compact-header">
                    <div>
                      <p className="kicker">Approval</p>
                      <h2>Decision trail</h2>
                    </div>
                  </div>
                  <div className="approval-summary">
                    <Metric label="Requested role" value={selectedCase.approvalState.requestedRole ?? 'Not required'} />
                    <Metric label="Requested at" value={formatDateOrPending(selectedCase.approvalState.requestedAt)} />
                    <Metric label="Decision by" value={selectedCase.approvalState.decisionBy ?? 'Pending'} />
                    <Metric label="Decision at" value={formatDateOrPending(selectedCase.approvalState.decisionAt)} />
                  </div>
                  {selectedCase.approvalState.comment ? (
                    <div className="approval-comment">{selectedCase.approvalState.comment}</div>
                  ) : null}
                  {selectedCase.approvalState.approvalRequired
                  && selectedCase.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' ? (
                    <form className="approval-form" onSubmit={handleApproval}>
                      <label>
                        <span>Decision</span>
                        <select
                          value={approvalForm.decision}
                          onChange={(event) => setApprovalForm((current) => ({ ...current, decision: event.target.value }))}
                        >
                          <option value="APPROVE">APPROVE</option>
                          <option value="REJECT">REJECT</option>
                        </select>
                      </label>
                      <label>
                        <span>Comment</span>
                        <textarea
                          rows={4}
                          value={approvalForm.comment}
                          onChange={(event) => setApprovalForm((current) => ({ ...current, comment: event.target.value }))}
                        />
                      </label>
                      <label>
                        <span>Actor id</span>
                        <input
                          value={approvalForm.actorId}
                          onChange={(event) => setApprovalForm((current) => ({ ...current, actorId: event.target.value }))}
                        />
                      </label>
                      <button type="submit" className="primary-button" disabled={isSubmittingApproval}>
                        {isSubmittingApproval ? 'Submitting...' : 'Submit approval'}
                      </button>
                    </form>
                  ) : null}

                  {selectedCase.outcome ? (
                    <div className="outcome-card">
                      <p className="kicker">Outcome</p>
                      <h3>{formatLabel(selectedCase.outcome.outcomeType)}</h3>
                      <p>{selectedCase.outcome.summary}</p>
                    </div>
                  ) : null}
                </div>
              </aside>
            </div>
          ) : null}
        </main>
      )}

      {isCreateModalOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setIsCreateModalOpen(false)}>
          <div className="modal-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="section-header">
              <div>
                <p className="kicker">New Case</p>
                <h2>Create intake ticket</h2>
              </div>
              <button type="button" className="ghost-button" onClick={() => setIsCreateModalOpen(false)}>
                Close
              </button>
            </div>
            <form className="create-form" onSubmit={handleCreateCase}>
              <label>
                <span>Scenario preset</span>
                <select
                  value={createScenarioId}
                  onChange={(event) => {
                    const nextScenario = SCENARIO_PRESETS.find((scenario) => scenario.id === event.target.value);
                    if (!nextScenario) {
                      return;
                    }
                    setCreateScenarioId(nextScenario.id);
                    setCreateForm((current) => ({
                      ...current,
                      caseType: nextScenario.caseType,
                      orderId: nextScenario.orderId,
                      amount: nextScenario.amount,
                      currency: nextScenario.currency,
                      initialMessage: nextScenario.initialMessage,
                    }));
                  }}
                >
                  {SCENARIO_PRESETS.map((scenario) => (
                    <option key={scenario.id} value={scenario.id}>{scenario.label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>Case type</span>
                <select
                  value={createForm.caseType}
                  onChange={(event) => setCreateForm((current) => ({ ...current, caseType: event.target.value as CaseType }))}
                >
                  {CASE_TYPES.map((caseType) => (
                    <option key={caseType} value={caseType}>{formatLabel(caseType)}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>Order id</span>
                <input
                  value={createForm.orderId}
                  onChange={(event) => setCreateForm((current) => ({ ...current, orderId: event.target.value }))}
                />
              </label>
              <label>
                <span>Amount</span>
                <input
                  value={createForm.amount}
                  onChange={(event) => setCreateForm((current) => ({ ...current, amount: event.target.value }))}
                />
              </label>
              <label>
                <span>Currency</span>
                <input
                  value={createForm.currency}
                  onChange={(event) => setCreateForm((current) => ({ ...current, currency: event.target.value.toUpperCase() }))}
                />
              </label>
              <label>
                <span>Operator id</span>
                <input
                  value={createForm.operatorId}
                  onChange={(event) => setCreateForm((current) => ({ ...current, operatorId: event.target.value }))}
                />
              </label>
              <label>
                <span>Operator role</span>
                <select
                  value={createForm.operatorRole}
                  onChange={(event) => setCreateForm((current) => ({ ...current, operatorRole: event.target.value }))}
                >
                  <option value="CASE_OPERATOR">CASE_OPERATOR</option>
                  <option value="FINANCE_CONTROL">FINANCE_CONTROL</option>
                </select>
              </label>
              <label className="create-form-full">
                <span>Initial case summary</span>
                <textarea
                  rows={5}
                  value={createForm.initialMessage}
                  onChange={(event) => setCreateForm((current) => ({ ...current, initialMessage: event.target.value }))}
                />
              </label>
              <button type="submit" className="primary-button" disabled={isSubmittingCreate}>
                {isSubmittingCreate ? 'Creating...' : 'Create case'}
              </button>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function SegmentButton({
  label,
  current,
  target,
  onChange,
}: {
  label: string;
  current: InboxFilter;
  target: InboxFilter;
  onChange: (value: InboxFilter) => void;
}) {
  const active = current === target;
  return (
    <button
      type="button"
      className={`segment-button ${active ? 'segment-button-active' : ''}`}
      aria-pressed={active}
      onClick={() => onChange(target)}
    >
      {label}
    </button>
  );
}

function StatusBadge({ value, variant }: { value: string; variant: 'status' | 'recommendation' | 'approval' | 'agent' }) {
  return <span className={`status-badge status-badge-${variant}`}>{value}</span>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EvidenceCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="evidence-card">
      <span>{label}</span>
      <p>{value}</p>
    </article>
  );
}

function ConversationBubble({
  entry,
  tone,
  compact = false,
}: {
  entry: TimelineEntry;
  tone: 'root' | 'reply' | 'system';
  compact?: boolean;
}) {
  return (
    <article className={`conversation-entry conversation-entry-${tone} ${compact ? 'conversation-entry-compact' : ''}`}>
      <div className="conversation-avatar">{initials(entry.actorName)}</div>
      <div className="conversation-body">
        <div className="conversation-head">
          <strong>{entry.actorName}</strong>
          <span>{entry.actorRole}</span>
          <time>{formatDate(entry.timestamp)}</time>
        </div>
        <h4>{entry.summary}</h4>
        <MarkdownText value={entry.body} />
        {entry.emphasis ? <div className="emphasis-line"><MarkdownText value={entry.emphasis} inline /></div> : null}
        {entry.meta?.length ? (
          <div className="meta-pill-row">
            {entry.meta.map((item) => (
              <span key={`${entry.id}-${item}`} className="meta-pill">{item}</span>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

function MarkdownText({ value, inline = false }: { value: string; inline?: boolean }) {
  const normalized = normalizeMarkdown(value);

  return (
    <div className={`markdown-content ${inline ? 'markdown-content-inline' : ''}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        components={{
          a: ({ node: _node, ...props }) => <a {...props} target="_blank" rel="noreferrer" />,
        }}
      >
        {normalized}
      </ReactMarkdown>
    </div>
  );
}

function buildQueueSummary(items: CaseListItem[]) {
  return {
    needsAction: items.filter((item) => item.pendingApproval || item.caseStatus === 'GATHERING_EVIDENCE').length,
    inFlight: items.filter((item) => item.caseStatus !== 'COMPLETED').length,
    completed: items.filter((item) => item.caseStatus === 'COMPLETED').length,
  };
}

function matchesInboxFilter(item: CaseListItem, filter: InboxFilter) {
  if (filter === 'all') {
    return true;
  }
  if (filter === 'needs-action') {
    return item.pendingApproval || item.caseStatus === 'GATHERING_EVIDENCE';
  }
  if (filter === 'in-flight') {
    return item.caseStatus !== 'COMPLETED' && !item.pendingApproval;
  }
  return item.caseStatus === 'COMPLETED';
}

function buildCaseHeadline(caseDetail: CaseDetailView) {
  return `${formatLabel(caseDetail.caseType)} for order ${caseDetail.orderId}`;
}

function buildCaseSummary(caseDetail: CaseDetailView) {
  const parts = [
    `Current recommendation: ${formatLabel(caseDetail.currentRecommendation)}.`,
    `Workflow status: ${formatLabel(caseDetail.caseStatus)}.`,
  ];

  if (caseDetail.approvalState.approvalRequired) {
    parts.push(`Approval status: ${formatLabel(caseDetail.approvalState.approvalStatus)}.`);
  }

  if (caseDetail.outcome) {
    parts.push(`Outcome recorded: ${formatLabel(caseDetail.outcome.outcomeType)}.`);
  }

  return parts.join(' ');
}

function buildAiBrief(caseDetail: CaseDetailView) {
  const latestEvent = [...caseDetail.activityHistory].pop();
  const brief = [
    `${formatLabel(caseDetail.caseStatus)} is the current workflow state for ${caseDetail.caseId}.`,
    `${formatLabel(caseDetail.currentRecommendation)} is the latest recommendation against ${formatCurrency(caseDetail.amount, caseDetail.currency)}.`,
  ];

  if (caseDetail.approvalState.approvalRequired) {
    brief.push(`Finance control is ${formatLabel(caseDetail.approvalState.approvalStatus)} for this case.`);
  }

  if (latestEvent) {
    brief.push(`Latest activity: ${latestEvent.message}`);
  }

  if (caseDetail.outcome) {
    brief.push(`Recorded outcome: ${formatLabel(caseDetail.outcome.outcomeType)}.`);
  }

  return brief;
}

function buildAgentRoster(events: ActivityEvent[]) {
  const seen = new Map<string, { name: string; role: string; status: string }>();

  for (const event of events) {
    const actor = toActorLabel(event.source);
    if (!actor.isAgent) {
      continue;
    }
    seen.set(actor.name, {
      name: actor.name,
      role: actor.role,
      status: activityStateLabel(event),
    });
  }

  return Array.from(seen.values());
}

function buildTimelineEntries(events: ActivityEvent[], caseDetail: CaseDetailView): TimelineEntry[] {
  const sorted = [...events].sort((left, right) => left.timestamp.localeCompare(right.timestamp));

  const entries = sorted.map((event) => {
    const payload = parseStructuredPayload(event.structuredPayload);
    const actor = toActorLabel(event.source);
    const meta = timelineMeta(event, payload);
    return {
      id: event.eventId,
      actorName: actor.name,
      actorRole: actor.role,
      kind: actor.isSystem ? 'system' : 'conversation',
      summary: timelineSummary(event, payload),
      body: timelineBody(event, payload),
      timestamp: event.timestamp,
      emphasis: timelineEmphasis(payload),
      meta,
    } satisfies TimelineEntry;
  });

  if (!entries.length) {
    return [
      {
        id: `${caseDetail.caseId}-empty`,
        actorName: 'Case system',
        actorRole: 'workflow',
        kind: 'system',
        summary: 'No activity yet',
        body: 'This case has not produced any activity history yet.',
        timestamp: new Date().toISOString(),
      },
    ];
  }

  return entries;
}

function buildConversationThreads(events: ActivityEvent[], caseDetail: CaseDetailView): ConversationThread[] {
  const entries = buildTimelineEntries(events, caseDetail);
  if (!entries.length) {
    return [];
  }

  const eventById = new Map(events.map((event) => [event.eventId, event]));
  const threads: ConversationThread[] = [];
  let current: ConversationThread | null = null;

  for (const entry of entries) {
    const rawEvent = eventById.get(entry.id);
    const startsThread = rawEvent ? isThreadRootEvent(rawEvent) : false;

    if (!current || startsThread) {
      if (current) {
        finalizeThread(current);
        threads.push(current);
      }
      current = {
        id: entry.id,
        title: threadTitle(entry, rawEvent),
        subtitle: threadSubtitle(rawEvent),
        root: entry,
        replies: [],
        systemEntries: [],
        participants: [entry.actorName],
        lastTimestamp: entry.timestamp,
      };
      continue;
    }

    current.lastTimestamp = entry.timestamp;
    if (!current.participants.includes(entry.actorName)) {
      current.participants.push(entry.actorName);
    }

    if (entry.kind === 'system') {
      current.systemEntries.push(entry);
      continue;
    }

    current.replies.push(entry);
  }

  if (current) {
    finalizeThread(current);
    threads.push(current);
  }

  return threads;
}

function isThreadRootEvent(event: ActivityEvent) {
  return event.kind === 'DELEGATION_STARTED' || event.kind === 'OPERATOR_INSTRUCTION_RECEIVED';
}

function threadTitle(entry: TimelineEntry, event: ActivityEvent | undefined) {
  if (!event) {
    return entry.summary;
  }
  if (event.kind === 'DELEGATION_STARTED') {
    return entry.body;
  }
  if (event.kind === 'OPERATOR_INSTRUCTION_RECEIVED') {
    return entry.body;
  }
  return entry.summary;
}

function threadSubtitle(event: ActivityEvent | undefined) {
  if (!event) {
    return 'Case thread';
  }
  if (event.kind === 'DELEGATION_STARTED') {
    return 'Case intake';
  }
  if (event.kind === 'OPERATOR_INSTRUCTION_RECEIVED') {
    return 'Follow-up request';
  }
  return friendlyKindLabel(event.kind);
}

function finalizeThread(thread: ConversationThread) {
  if (!thread.replies.length && thread.systemEntries.length) {
    const firstVisibleSystemEntry = thread.systemEntries.shift();
    if (firstVisibleSystemEntry) {
      thread.replies.push(firstVisibleSystemEntry);
      if (!thread.participants.includes(firstVisibleSystemEntry.actorName)) {
        thread.participants.push(firstVisibleSystemEntry.actorName);
      }
    }
  }
}

function timelineSummary(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const kind = event.kind;
  if (kind === 'OPERATOR_INSTRUCTION_RECEIVED') {
    return 'Operator sent a new request';
  }
  if (kind === 'DELEGATION_STARTED') {
    return 'Case started with the intake request';
  }
  if (kind === 'APPROVAL_REQUESTED') {
    return 'Finance control approval requested';
  }
  if (kind === 'OPERATOR_REQUEST_COMPLETED') {
    return 'Workflow agent posted a case update';
  }
  if (typeof payload?.type === 'string' && payload.type === 'agent_response') {
    return 'Specialist agent responded';
  }
  if (typeof payload?.type === 'string' && payload.type === 'tool_call') {
    return 'Tool execution recorded';
  }
  return formatLabel(kind);
}

function timelineBody(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const instruction = payloadInstruction(payload);
  if ((event.kind === 'OPERATOR_INSTRUCTION_RECEIVED' || event.kind === 'DELEGATION_STARTED') && instruction) {
    return instruction;
  }
  return event.message;
}

function timelineEmphasis(payload: Record<string, unknown> | null) {
  if (!payload) {
    return undefined;
  }

  const keys = ['recommendation', 'approvalStatus', 'agent', 'toolName', 'requestedRole', 'focus'];
  for (const key of keys) {
    const value = payload[key];
    if (typeof value === 'string' && value) {
      return `${formatLabel(key)}: ${formatLabel(value)}`;
    }
  }

  if (Array.isArray(payload.delegatedAgents) && payload.delegatedAgents.length) {
    return `delegated agents: ${payload.delegatedAgents.map((item) => formatValue(item)).join(', ')}`;
  }

  return undefined;
}

function payloadInstruction(payload: Record<string, unknown> | null) {
  const instruction = payload?.instruction;
  return typeof instruction === 'string' && instruction.trim() ? instruction.trim() : null;
}

function timelineMeta(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const meta = [friendlyKindLabel(event.kind)];

  const toolName = typeof payload?.toolName === 'string' ? payload.toolName : null;
  if (toolName) {
    meta.push(`tool ${formatLabel(toolName)}`);
  }

  const requestedRole = typeof payload?.requestedRole === 'string' ? payload.requestedRole : null;
  if (requestedRole) {
    meta.push(`role ${formatLabel(requestedRole)}`);
  }

  return meta;
}

function toActorLabel(source: string) {
  const normalized = source.toLowerCase();
  if (normalized.includes('shipment-agent')) {
    return { name: 'Shipment Agent', role: 'service agent', isAgent: true, isSystem: false };
  }
  if (normalized.includes('escrow-agent')) {
    return { name: 'Escrow Agent', role: 'service agent', isAgent: true, isSystem: false };
  }
  if (normalized.includes('risk-agent')) {
    return { name: 'Risk Agent', role: 'service agent', isAgent: true, isSystem: false };
  }
  if (normalized.includes('case-workflow-agent') || normalized.includes('workflow-agent')) {
    return { name: 'Workflow Agent', role: 'coordinator', isAgent: true, isSystem: false };
  }
  if (normalized.includes('hook')) {
    return { name: 'Approval Hook', role: 'policy control', isAgent: false, isSystem: true };
  }
  if (normalized.includes('runtime') || normalized.includes('steering') || normalized.includes('security')) {
    return { name: 'Runtime', role: 'system', isAgent: false, isSystem: true };
  }
  return { name: formatSourceName(source), role: 'activity source', isAgent: false, isSystem: true };
}

function activityStateLabel(event: ActivityEvent) {
  if (event.kind === 'APPROVAL_REQUESTED') {
    return 'awaiting approval';
  }
  if (event.kind === 'OPERATOR_REQUEST_COMPLETED') {
    return 'responded';
  }
  if (event.kind.includes('TOOL')) {
    return 'used tools';
  }
  return 'active';
}

function parseStructuredPayload(payload: string): Record<string, unknown> | null {
  if (!payload || payload === '{}') {
    return null;
  }

  try {
    const parsed = JSON.parse(payload) as Record<string, unknown>;
    return Object.keys(parsed).length ? parsed : null;
  } catch {
    return { rawPayload: payload };
  }
}

function formatApprovalState(item: CaseListItem) {
  if (item.pendingApproval && item.requestedRole) {
    return `${formatLabel(item.approvalStatus)} / ${formatLabel(item.requestedRole)}`;
  }
  return formatLabel(item.approvalStatus);
}

function formatLabel(value: string) {
  return value.replaceAll('_', ' ');
}

function formatSourceName(source: string) {
  return source
    .split(/[-_]/g)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(' ');
}

function friendlyKindLabel(kind: string) {
  switch (kind) {
    case 'OPERATOR_INSTRUCTION_RECEIVED':
      return 'operator request';
    case 'DELEGATION_ROUTED':
      return 'delegation';
    case 'AGENT_RESPONSE':
      return 'agent response';
    case 'OPERATOR_REQUEST_COMPLETED':
      return 'completed update';
    case 'DELEGATION_STARTED':
      return 'case intake';
    case 'EVIDENCE_RECEIVED':
      return 'evidence received';
    case 'STREAM_PROGRESS':
      return 'streaming progress';
    case 'HOOK_FORCED_TOOL_SELECTION':
      return 'policy intervention';
    case 'TOOL_CALL_RECORDED':
      return 'tool call';
    case 'APPROVAL_INTERRUPT_REGISTERED':
      return 'approval pause';
    case 'APPROVAL_REQUESTED':
      return 'approval requested';
    case 'RECOMMENDATION_UPDATED':
      return 'recommendation updated';
    default:
      return formatLabel(kind);
  }
}

function formatCurrency(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function formatDateOrPending(value: string | null) {
  if (!value) {
    return 'Pending';
  }
  return formatDate(value);
}

function formatRelativeDate(value: string) {
  const date = new Date(value);
  const diffMinutes = Math.round((date.getTime() - Date.now()) / 60000);
  const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

  if (Math.abs(diffMinutes) < 60) {
    return formatter.format(diffMinutes, 'minute');
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (Math.abs(diffHours) < 24) {
    return formatter.format(diffHours, 'hour');
  }

  const diffDays = Math.round(diffHours / 24);
  return formatter.format(diffDays, 'day');
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map((item) => formatValue(item)).join(', ');
  }
  if (typeof value === 'string') {
    return formatLabel(value);
  }
  return String(value);
}

function initials(value: string) {
  const tokens = value.split(' ').filter(Boolean).slice(0, 2);
  if (!tokens.length) {
    return 'AC';
  }
  return tokens.map((token) => token.charAt(0).toUpperCase()).join('');
}

function normalizeMarkdown(value: string) {
  return value
    .replace(/\\n/g, '\n')
    .replace(/([^\n])\s(?=#{1,6}\s)/g, '$1\n\n')
    .replace(/([^\n])\s(?=-\s+(?:\*\*|`|[A-Za-z0-9\u3040-\u30ff\u3400-\u9fff]))/g, '$1\n')
    .replace(/([^\n])\s(?=\d+\.\s)/g, '$1\n')
    .trim();
}

export default App;