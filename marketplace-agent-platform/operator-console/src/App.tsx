import { FormEvent, startTransition, useEffect, useMemo, useState } from 'react';
import {
  ActivityEvent,
  ActivityCategory,
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

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
});

function App() {
  const apiEndpointLabel = API_BASE_URL || window.location.origin;
  const [cases, setCases] = useState<CaseListItem[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState<string>('');
  const [selectedCase, setSelectedCase] = useState<CaseDetailView | null>(null);
  const [searchText, setSearchText] = useState('');
  const [searchDraft, setSearchDraft] = useState('');
  const [caseTypeFilter, setCaseTypeFilter] = useState<string>('');
  const [caseStatusFilter, setCaseStatusFilter] = useState<string>('');
  const [isLoadingCases, setIsLoadingCases] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [isSubmittingMessage, setIsSubmittingMessage] = useState(false);
  const [isSubmittingApproval, setIsSubmittingApproval] = useState(false);
  const [activityFilter, setActivityFilter] = useState<'all' | ActivityCategory>('all');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [flashMessage, setFlashMessage] = useState<string>('');
  const [createForm, setCreateForm] = useState({
    caseType: 'ITEM_NOT_RECEIVED' as CaseType,
    orderId: 'order-1001',
    amount: '149.95',
    currency: 'USD',
    initialMessage: 'Buyer reports item not received.',
    operatorId: 'operator-1',
    operatorRole: 'CASE_OPERATOR',
  });
  const [messageForm, setMessageForm] = useState({
    message: 'Please summarize the shipment evidence.',
    operatorId: 'operator-1',
    operatorRole: 'CASE_OPERATOR',
  });
  const [approvalForm, setApprovalForm] = useState({
    decision: 'APPROVE',
    comment: 'Proceed with the current recommendation.',
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
    if (!selectedCaseId) {
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
  }, [selectedCaseId]);

  const groupedEvidence = useMemo(() => {
    if (!selectedCase) {
      return [];
    }
    return [
      { label: 'Shipment', value: selectedCase.evidence.shipmentEvidence },
      { label: 'Escrow', value: selectedCase.evidence.escrowEvidence },
      { label: 'Risk', value: selectedCase.evidence.riskEvidence },
      { label: 'Policy', value: selectedCase.evidence.policyReference },
    ];
  }, [selectedCase]);

  const activityTimeline = useMemo(() => {
    if (!selectedCase) {
      return [] as Array<ActivityTimelineEntry>;
    }
    return selectedCase.activityHistory.map((event) => {
      const payload = parseStructuredPayload(event.structuredPayload);
      return {
        event,
        payload,
        category: classifyActivity(event, payload),
      };
    });
  }, [selectedCase]);

  const activityCounts = useMemo(() => countActivityCategories(activityTimeline), [activityTimeline]);

  const delegationTrace = useMemo(() => buildDelegationTrace(activityTimeline), [activityTimeline]);

  const filteredActivityTimeline = useMemo(() => {
    if (activityFilter === 'all') {
      return activityTimeline;
    }
    return activityTimeline.filter((entry) => entry.category === activityFilter);
  }, [activityFilter, activityTimeline]);

  const displayedActivityTimeline = useMemo(
    () => collapseDuplicateActivities(filteredActivityTimeline),
    [filteredActivityTimeline],
  );

  const activityBlocks = useMemo(() => buildActivityBlocks(displayedActivityTimeline), [displayedActivityTimeline]);

  const filteredDelegationTrace = useMemo(() => {
    if (activityFilter === 'all') {
      return delegationTrace;
    }
    return delegationTrace.filter((entry) => entry.category === activityFilter);
  }, [activityFilter, delegationTrace]);

  const latestWorkflowCompletion = useMemo(() => {
    return [...activityTimeline].reverse().find((entry) => entry.payload?.type === 'workflow_completion') ?? null;
  }, [activityTimeline]);

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
        startTransition(() => setSelectedCaseId(payload[0]?.caseId ?? ''));
      }
      if (!selectedCaseId && payload.length > 0) {
        startTransition(() => setSelectedCaseId(payload[0].caseId));
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
      startTransition(() => setSelectedCaseId(payload.caseId));
      await loadCases(false);
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
      setFlashMessage(`Case ${payload.caseId} updated with a new operator message.`);
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
    <div className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Marketplace Agent Platform</p>
          <h1>Operator Console</h1>
        </div>
        <div className="endpoint-card">
          <span>Console origin</span>
          <strong>{apiEndpointLabel}</strong>
        </div>
      </header>

      {(errorMessage || flashMessage) && (
        <section className="banner-row">
          {errorMessage && <div className="banner banner-error">{errorMessage}</div>}
          {!errorMessage && flashMessage && <div className="banner banner-success">{flashMessage}</div>}
        </section>
      )}

      <main className="workspace">
        <section className="panel panel-list">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Case List</p>
              <h2>Operator queue</h2>
            </div>
            <span className="status-chip">{isLoadingCases ? 'Refreshing' : `${cases.length} in queue`}</span>
          </div>

          <form
            className="search-form"
            onSubmit={(event) => {
              event.preventDefault();
              setSearchText(searchDraft);
            }}
          >
            <input
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              placeholder="Search by case id, order id, state, recommendation, or outcome"
            />
            <select value={caseTypeFilter} onChange={(event) => setCaseTypeFilter(event.target.value)}>
              <option value="">All types</option>
              {CASE_TYPES.map((caseType) => (
                <option key={caseType} value={caseType}>{formatLabel(caseType)}</option>
              ))}
            </select>
            <select value={caseStatusFilter} onChange={(event) => setCaseStatusFilter(event.target.value)}>
              <option value="">All workflow states</option>
              {CASE_STATUS_FILTERS.map((caseStatus) => (
                <option key={caseStatus} value={caseStatus}>{formatLabel(caseStatus)}</option>
              ))}
            </select>
            <button type="submit">Search</button>
            <button
              type="button"
              className="search-reset"
              onClick={() => {
                setSearchDraft('');
                setSearchText('');
                setCaseTypeFilter('');
                setCaseStatusFilter('');
              }}
            >
              Clear
            </button>
          </form>

          <div className="case-list" role="list">
            {cases.map((item) => (
              <button
                type="button"
                key={item.caseId}
                className={`case-card ${item.caseId === selectedCaseId ? 'case-card-active' : ''}`}
                onClick={() => startTransition(() => setSelectedCaseId(item.caseId))}
              >
                <div className="case-card-top">
                  <strong>{item.caseType}</strong>
                  <span className={`pill pill-${item.caseStatus.toLowerCase()}`}>{formatLabel(item.caseStatus)}</span>
                </div>
                <p>{item.caseId}</p>
                <p>Order {item.orderId}</p>
                <div className="case-card-state-row">
                  <span className="case-state-label">Recommendation {formatLabel(item.currentRecommendation)}</span>
                  <span className={`pill pill-${item.approvalStatus.toLowerCase()}`}>{formatApprovalState(item)}</span>
                </div>
                {item.outcomeType && (
                  <div className="case-card-state-row">
                    <span className="case-state-label">Outcome {formatLabel(item.outcomeType)}</span>
                  </div>
                )}
                <div className="case-card-bottom">
                  <span>{formatCurrency(item.amount, item.currency)}</span>
                  <span>{formatDate(item.updatedAt)}</span>
                </div>
              </button>
            ))}
            {!cases.length && !isLoadingCases && <div className="empty-state">No cases loaded yet. Create the first operator case below.</div>}
          </div>

          <div className="panel-divider" />

          <div className="panel-header compact">
            <div>
              <p className="eyebrow">New Case</p>
              <h3>Case intake</h3>
            </div>
          </div>

          <form className="form-grid" onSubmit={handleCreateCase}>
            <label>
              Case type
              <select
                value={createForm.caseType}
                onChange={(event) => setCreateForm((current) => ({ ...current, caseType: event.target.value as CaseType }))}
              >
                {CASE_TYPES.map((caseType) => (
                  <option key={caseType} value={caseType}>{caseType}</option>
                ))}
              </select>
            </label>
            <label>
              Order id
              <input value={createForm.orderId} onChange={(event) => setCreateForm((current) => ({ ...current, orderId: event.target.value }))} />
            </label>
            <label>
              Amount
              <input value={createForm.amount} onChange={(event) => setCreateForm((current) => ({ ...current, amount: event.target.value }))} />
            </label>
            <label>
              Currency
              <input value={createForm.currency} onChange={(event) => setCreateForm((current) => ({ ...current, currency: event.target.value.toUpperCase() }))} />
            </label>
            <label>
              Operator id
              <input value={createForm.operatorId} onChange={(event) => setCreateForm((current) => ({ ...current, operatorId: event.target.value }))} />
            </label>
            <label>
              Operator role
              <select
                value={createForm.operatorRole}
                onChange={(event) => setCreateForm((current) => ({ ...current, operatorRole: event.target.value }))}
              >
                <option value="CASE_OPERATOR">CASE_OPERATOR</option>
                <option value="FINANCE_CONTROL">FINANCE_CONTROL</option>
              </select>
            </label>
            <label className="field-span-2">
              Initial message
              <textarea
                rows={4}
                value={createForm.initialMessage}
                onChange={(event) => setCreateForm((current) => ({ ...current, initialMessage: event.target.value }))}
              />
            </label>
            <button className="primary-action" type="submit" disabled={isSubmittingCreate}>
              {isSubmittingCreate ? 'Creating case...' : 'Create case'}
            </button>
          </form>
        </section>

        <section className="panel panel-detail">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Case Detail</p>
              <h2>{selectedCase ? selectedCase.caseType : 'Select a case'}</h2>
            </div>
            {selectedCase && <span className="status-chip">{selectedCase.caseId}</span>}
          </div>

          {!selectedCase && !isLoadingDetail && <div className="empty-state large">Pick a case from the queue or create a new one to inspect the full workflow state.</div>}

          {selectedCase && (
            <div className="detail-layout">
              <section className="detail-card detail-card-wide">
                <div className="card-header">
                  <h3>Case Summary</h3>
                  <span className={`pill pill-${selectedCase.caseStatus.toLowerCase()}`}>{formatLabel(selectedCase.caseStatus)}</span>
                </div>
                <div className="summary-grid">
                  <SummaryField label="Case id" value={selectedCase.caseId} />
                  <SummaryField label="Order id" value={selectedCase.orderId} />
                  <SummaryField label="Transaction id" value={selectedCase.transactionId} />
                  <SummaryField label="Workflow status" value={formatLabel(selectedCase.caseStatus)} />
                  <SummaryField label="Recommendation" value={formatLabel(selectedCase.currentRecommendation)} />
                  <SummaryField label="Amount" value={formatCurrency(selectedCase.amount, selectedCase.currency)} />
                  <SummaryField label="Approval status" value={formatLabel(selectedCase.approvalState.approvalStatus)} />
                  <SummaryField label="Approval required" value={selectedCase.approvalState.approvalRequired ? 'Yes' : 'No'} />
                  <SummaryField label="Outcome" value={selectedCase.outcome ? formatLabel(selectedCase.outcome.outcomeType) : 'Pending'} />
                </div>
              </section>

              <section className="detail-card detail-card-wide">
                <div className="card-header">
                  <h3>Evidence</h3>
                  <span className="subtle-label">Structured output</span>
                </div>
                <div className="evidence-grid">
                  {groupedEvidence.map((item) => (
                    <article key={item.label} className="evidence-item">
                      <p className="evidence-label">{item.label}</p>
                      <p>{item.value}</p>
                    </article>
                  ))}
                </div>
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <h3>Operator Instruction</h3>
                  <span className="subtle-label">workflow-agent delegation</span>
                </div>
                <p className="instruction-hint">The workflow-agent routes this instruction to shipment-agent, escrow-agent, or risk-agent and records the delegation trail below.</p>
                <form className="stack-form" onSubmit={handleAddMessage}>
                  <label>
                    Message
                    <textarea
                      rows={5}
                      value={messageForm.message}
                      onChange={(event) => setMessageForm((current) => ({ ...current, message: event.target.value }))}
                    />
                  </label>
                  <label>
                    Operator id
                    <input value={messageForm.operatorId} onChange={(event) => setMessageForm((current) => ({ ...current, operatorId: event.target.value }))} />
                  </label>
                  <label>
                    Operator role
                    <select
                      value={messageForm.operatorRole}
                      onChange={(event) => setMessageForm((current) => ({ ...current, operatorRole: event.target.value }))}
                    >
                      <option value="CASE_OPERATOR">CASE_OPERATOR</option>
                      <option value="FINANCE_CONTROL">FINANCE_CONTROL</option>
                    </select>
                  </label>
                  <button className="secondary-action" type="submit" disabled={isSubmittingMessage}>
                    {isSubmittingMessage ? 'Delegating...' : 'Delegate via workflow-agent'}
                  </button>
                </form>
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <h3>Agent Orchestration</h3>
                  <span className="subtle-label">Latest completion</span>
                </div>
                {latestWorkflowCompletion ? (
                  <div className="orchestration-card">
                    <p className="eyebrow">workflow-agent summary</p>
                    <p className="orchestration-message">{latestWorkflowCompletion.event.message}</p>
                    <div className="payload-grid">
                      <PayloadRow label="Delegated by" value={latestWorkflowCompletion.payload?.delegatedBy} />
                      <PayloadRow label="Agents" value={latestWorkflowCompletion.payload?.delegatedAgents} />
                      <PayloadRow label="Recommendation" value={latestWorkflowCompletion.payload?.recommendation} />
                      <PayloadRow label="Approval status" value={latestWorkflowCompletion.payload?.approvalStatus} />
                    </div>
                  </div>
                ) : (
                  <div className="empty-state">Submit an operator instruction to see which agents the workflow-agent selected and how it closed the request.</div>
                )}
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <h3>Delegation Trace</h3>
                  <span className="subtle-label">agent runtime tool hook</span>
                </div>
                {filteredDelegationTrace.length ? (
                  <div className="trace-stack">
                    {filteredDelegationTrace.map((entry) => (
                      <article key={entry.eventId} className="trace-item">
                        <div className="trace-header">
                          <span className={`category-pill category-${entry.category}`}>{entry.category}</span>
                          <strong>{entry.title}</strong>
                        </div>
                        <div className="trace-lane">
                          <div className="trace-node trace-node-source">
                            <span className="trace-node-label">Source</span>
                            <strong>{entry.source}</strong>
                          </div>
                          <div className="trace-arrow" aria-hidden="true">→</div>
                          <div className="trace-node trace-node-target">
                            <span className="trace-node-label">Target</span>
                            <strong>{entry.target}</strong>
                          </div>
                        </div>
                        {entry.highlight ? <p className="trace-highlight">{entry.highlight}</p> : null}
                        <p>{entry.description}</p>
                      </article>
                    ))}
                  </div>
                ) : (
                  <div className="empty-state">No delegation entries match the current category filter.</div>
                )}
              </section>

              <section className="detail-card">
                <div className="card-header">
                  <h3>Approval And Outcome</h3>
                  <span className="subtle-label">Finance control gate</span>
                </div>
                <div className="approval-block">
                  <SummaryField label="Requested role" value={selectedCase.approvalState.requestedRole ?? 'Not required'} />
                  <SummaryField label="Requested at" value={formatDate(selectedCase.approvalState.requestedAt)} />
                  <SummaryField label="Decision by" value={selectedCase.approvalState.decisionBy ?? 'Pending'} />
                  <SummaryField label="Decision at" value={formatDate(selectedCase.approvalState.decisionAt)} />
                  <SummaryField label="Approval comment" value={selectedCase.approvalState.comment ?? 'No decision comment'} />
                </div>
                {selectedCase.approvalState.approvalRequired && selectedCase.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' && (
                  <form className="stack-form" onSubmit={handleApproval}>
                    <label>
                      Decision
                      <select
                        value={approvalForm.decision}
                        onChange={(event) => setApprovalForm((current) => ({ ...current, decision: event.target.value }))}
                      >
                        <option value="APPROVE">APPROVE</option>
                        <option value="REJECT">REJECT</option>
                      </select>
                    </label>
                    <label>
                      Comment
                      <textarea
                        rows={4}
                        value={approvalForm.comment}
                        onChange={(event) => setApprovalForm((current) => ({ ...current, comment: event.target.value }))}
                      />
                    </label>
                    <label>
                      Actor id
                      <input value={approvalForm.actorId} onChange={(event) => setApprovalForm((current) => ({ ...current, actorId: event.target.value }))} />
                    </label>
                    <label>
                      Actor role
                      <select
                        value={approvalForm.actorRole}
                        onChange={(event) => setApprovalForm((current) => ({ ...current, actorRole: event.target.value }))}
                      >
                        <option value="FINANCE_CONTROL">FINANCE_CONTROL</option>
                      </select>
                    </label>
                    <button className="primary-action" type="submit" disabled={isSubmittingApproval}>
                      {isSubmittingApproval ? 'Submitting approval...' : 'Submit approval'}
                    </button>
                  </form>
                )}
                {!selectedCase.outcome && selectedCase.approvalState.approvalStatus === 'REJECTED' && (
                  <div className="outcome-card">
                    <p className="eyebrow">Outcome</p>
                    <h4>Pending further evidence</h4>
                    <p>Finance control rejected the current recommendation. The workflow returned this case to evidence gathering and no settlement has been recorded yet.</p>
                  </div>
                )}
                {selectedCase.outcome && (
                  <div className="outcome-card">
                    <p className="eyebrow">Outcome</p>
                    <h4>{selectedCase.outcome.outcomeType.replaceAll('_', ' ')}</h4>
                    <p>{selectedCase.outcome.summary}</p>
                    <div className="summary-grid narrow">
                      <SummaryField label="Status" value={selectedCase.outcome.outcomeStatus} />
                      <SummaryField label="Settlement reference" value={selectedCase.outcome.settlementReference} />
                      <SummaryField label="Settled at" value={formatDate(selectedCase.outcome.settledAt)} />
                    </div>
                  </div>
                )}
              </section>

              <section className="detail-card detail-card-wide">
                <div className="card-header">
                  <h3>Activity History</h3>
                  <span className="subtle-label">SSE-fed agent timeline</span>
                </div>
                <div className="activity-guide">
                  <div className="activity-guide-header">
                    <strong>How to read this timeline</strong>
                    <span className="subtle-label">top to bottom in time order</span>
                  </div>
                  <div className="activity-guide-grid">
                    <article className="activity-guide-card">
                      <span className="activity-guide-step">1</span>
                      <strong>Who acted</strong>
                      <p>The source tells you which agent, runtime component, tool, or hook produced the entry.</p>
                    </article>
                    <article className="activity-guide-card">
                      <span className="activity-guide-step">2</span>
                      <strong>What kind of action</strong>
                      <p>The category pill shows whether the entry is an agent action, runtime control step, tool call, or hook intervention.</p>
                    </article>
                    <article className="activity-guide-card">
                      <span className="activity-guide-step">3</span>
                      <strong>Why it matters</strong>
                      <p>The fact pills surface the key fields first, such as delegated agent, tool name, focus, recommendation, or approval gate.</p>
                    </article>
                    <article className="activity-guide-card">
                      <span className="activity-guide-step">4</span>
                      <strong>Turn boundary</strong>
                      <p>Each block groups one operator turn or one workflow phase. Open the nested steps only when you need event-level detail.</p>
                    </article>
                  </div>
                </div>
                <div className="filter-bar" role="tablist" aria-label="Activity categories">
                  <FilterChip
                    label="All"
                    value={activityFilter}
                    target="all"
                    onSelect={setActivityFilter}
                    count={collapseDuplicateActivities(activityTimeline).length}
                  />
                  <FilterChip
                    label="Agent"
                    value={activityFilter}
                    target="agent"
                    onSelect={setActivityFilter}
                    count={activityCounts.agent}
                  />
                  <FilterChip
                    label="Runtime"
                    value={activityFilter}
                    target="runtime"
                    onSelect={setActivityFilter}
                    count={activityCounts.runtime}
                  />
                  <FilterChip
                    label="Tool"
                    value={activityFilter}
                    target="tool"
                    onSelect={setActivityFilter}
                    count={activityCounts.tool}
                  />
                  <FilterChip
                    label="Hook"
                    value={activityFilter}
                    target="hook"
                    onSelect={setActivityFilter}
                    count={activityCounts.hook}
                  />
                </div>
                <div className="category-summary-row">
                  <SummaryPill label="Agent" value={activityCounts.agent} category="agent" />
                  <SummaryPill label="Runtime" value={activityCounts.runtime} category="runtime" />
                  <SummaryPill label="Tool" value={activityCounts.tool} category="tool" />
                  <SummaryPill label="Hook" value={activityCounts.hook} category="hook" />
                </div>
                <div className="timeline">
                  {activityBlocks.map((block) => (
                    <section key={block.id} className="activity-block">
                      <div className="activity-block-header">
                        <div>
                          <p className="eyebrow">{block.phaseLabel}</p>
                          <h4>{block.title}</h4>
                        </div>
                        <div className="activity-block-meta">
                          <span className="status-chip">{block.entries.length} steps</span>
                          <span>{formatDate(block.entries[0]?.event.timestamp ?? null)}</span>
                        </div>
                      </div>
                      {block.subtitle ? <p className="activity-block-subtitle">{block.subtitle}</p> : null}
                      <div className="activity-step-list">
                        {block.entries.map(({ event, payload, category, duplicateCount }) => {
                          const payloadType = typeof payload?.type === 'string' ? payload.type : null;
                          return (
                            <article key={event.eventId} className="timeline-item timeline-item-nested">
                              <div className="timeline-meta">
                                <strong>{friendlyKindLabel(event.kind)}</strong>
                                <span>{event.source}</span>
                                <span className={`category-pill category-${category}`} title={categoryDescription(category)}>{category}</span>
                                {payloadType ? <span className="subtle-label timeline-label">{formatLabel(payloadType)}</span> : null}
                                {duplicateCount > 1 ? <span className="duplicate-pill">shown {duplicateCount}x</span> : null}
                              </div>
                              {payload && timelineKeyFacts(payload).length ? (
                                <div className="timeline-facts">
                                  {timelineKeyFacts(payload).map((fact) => (
                                    <span key={`${event.eventId}-${fact.label}`} className="timeline-fact">
                                      <span>{fact.label}</span>
                                      <strong>{fact.value}</strong>
                                    </span>
                                  ))}
                                </div>
                              ) : null}
                              <p>{event.message}</p>
                              {payload && hasPayloadFields(payload) ? <PayloadDetails payload={payload} /> : null}
                            </article>
                          );
                        })}
                      </div>
                    </section>
                  ))}
                  {!activityBlocks.length && <div className="empty-state">No activity entries match the current category filter.</div>}
                </div>
              </section>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

function FilterChip({
  label,
  value,
  target,
  onSelect,
  count,
}: {
  label: string;
  value: 'all' | ActivityCategory;
  target: 'all' | ActivityCategory;
  onSelect: (next: 'all' | ActivityCategory) => void;
  count: number;
}) {
  const active = value === target;
  return (
    <button
      type="button"
      className={`filter-chip ${active ? 'filter-chip-active' : ''}`}
      onClick={() => onSelect(target)}
      aria-pressed={active}
    >
      <span>{label}</span>
      <strong>{count}</strong>
    </button>
  );
}

function SummaryPill({ label, value, category }: { label: string; value: number; category: ActivityCategory }) {
  return (
    <div className="summary-pill">
      <span>{label}</span>
      <strong className={`category-pill category-${category}`}>{value}</strong>
    </div>
  );
}

function SummaryField({ label, value }: { label: string; value: string }) {
  return (
    <div className="summary-field">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function PayloadDetails({ payload }: { payload: Record<string, unknown> }) {
  const entries = orderedPayloadEntries(payload);

  if (!entries.length) {
    return null;
  }

  return (
    <details className="payload-details">
      <summary>Structured details</summary>
      <div className="payload-grid">
        {entries.map(([label, value]) => (
          <PayloadRow key={label} label={formatLabel(label)} value={value} />
        ))}
      </div>
    </details>
  );
}

function PayloadRow({ label, value }: { label: string; value: unknown }) {
  if (value == null || value === '') {
    return null;
  }

  if (Array.isArray(value)) {
    return (
      <div className="payload-row">
        <span>{label}</span>
        <div className="payload-pill-row">
          {value.map((item) => (
            <strong key={`${label}-${String(item)}`} className="payload-pill">{formatValue(item)}</strong>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="payload-row">
      <span>{label}</span>
      <strong>{formatValue(value)}</strong>
    </div>
  );
}

function formatCurrency(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch {
    return `${currencyFormatter.format(amount)} ${currency}`;
  }
}

function formatApprovalState(item: CaseListItem) {
  if (item.pendingApproval && item.requestedRole) {
    return `${formatLabel(item.approvalStatus)} · ${formatLabel(item.requestedRole)}`;
  }
  return formatLabel(item.approvalStatus);
}

function formatLabel(value: string) {
  return value.replaceAll('_', ' ');
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Pending';
  }
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
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

function classifyActivity(event: ActivityEvent, payload: Record<string, unknown> | null): ActivityCategory {
  const payloadType = typeof payload?.type === 'string' ? payload.type : '';
  const source = event.source.toLowerCase();
  const kind = event.kind.toUpperCase();

  if (payloadType === 'hook_event' || kind.includes('HOOK')) {
    return 'hook';
  }
  if (payloadType === 'tool_call' || payloadType === 'tool_result' || kind.includes('TOOL') || kind.includes('STEERING')) {
    return 'tool';
  }
  if (payloadType === 'runtime_event' || source.includes('workflow-runtime') || source.includes('workflow-security') || source.includes('workflow-steering')) {
    return 'runtime';
  }
  return 'agent';
}

function categoryDescription(category: ActivityCategory) {
  switch (category) {
    case 'agent':
      return 'agent action';
    case 'runtime':
      return 'runtime control';
    case 'tool':
      return 'tool call';
    case 'hook':
      return 'hook intervention';
    default:
      return category;
  }
}

function friendlyKindLabel(kind: string) {
  switch (kind) {
    case 'OPERATOR_INSTRUCTION_RECEIVED':
      return 'Operator request received';
    case 'DELEGATION_ROUTED':
      return 'Delegation routed';
    case 'AGENT_RESPONSE':
      return 'Specialist response';
    case 'OPERATOR_REQUEST_COMPLETED':
      return 'Operator request completed';
    case 'DELEGATION_STARTED':
      return 'Case intake started';
    case 'EVIDENCE_RECEIVED':
      return 'Evidence received';
    case 'STREAM_PROGRESS':
      return 'Workflow consulted guidance';
    case 'HOOK_FORCED_TOOL_SELECTION':
      return 'Hook forced tool choice';
    case 'TOOL_CALL_RECORDED':
      return 'Tool invoked';
    case 'APPROVAL_INTERRUPT_REGISTERED':
      return 'Approval pause registered';
    case 'APPROVAL_REQUESTED':
      return 'Approval requested';
    case 'RECOMMENDATION_UPDATED':
      return 'Recommendation updated';
    default:
      return formatLabel(kind);
  }
}

function countActivityCategories(entries: ActivityTimelineEntry[]) {
  return collapseDuplicateActivities(entries).reduce(
    (counts, entry) => {
      counts[entry.category] += 1;
      return counts;
    },
    { agent: 0, runtime: 0, tool: 0, hook: 0 } as Record<ActivityCategory, number>,
  );
}

function buildDelegationTrace(entries: ActivityTimelineEntry[]) {
  return entries
    .filter(({ payload, event }) => {
      const payloadType = typeof payload?.type === 'string' ? payload.type : '';
      return payloadType === 'delegation_start'
        || payloadType === 'operator_instruction'
        || payloadType === 'delegation_assignment'
        || payloadType === 'agent_response'
        || payloadType === 'workflow_completion'
        || payloadType === 'tool_call'
        || payloadType === 'tool_result'
        || payloadType === 'hook_event'
        || payloadType === 'runtime_event'
        || event.kind === 'APPROVAL_REQUESTED';
    })
    .map(({ event, payload, category }) => ({
      eventId: event.eventId,
      category,
      title: traceTitle(event, payload),
      source: traceSource(event, payload),
      target: traceTarget(event, payload),
      highlight: traceHighlight(payload),
      description: event.message,
    }));
}

function traceTitle(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const payloadType = typeof payload?.type === 'string' ? payload.type : '';
  if (payloadType === 'delegation_assignment') {
    return `${formatValue(payload?.delegatedBy)} -> ${formatValue(payload?.agent)}`;
  }
  if (payloadType === 'delegation_start') {
    return `${formatValue(payload?.delegatedBy)} -> ${formatValue(payload?.delegatedAgents)}`;
  }
  if (payloadType === 'hook_event') {
    return `Hook forced ${formatValue(payload?.toolName)}`;
  }
  if (payloadType === 'tool_call' || payloadType === 'tool_result') {
    return `${formatValue(payload?.toolName)} via ${event.source}`;
  }
  if (payloadType === 'workflow_completion') {
    return `${formatValue(payload?.delegatedBy)} completed operator request`;
  }
  return `${event.source} ${formatLabel(event.kind).toLowerCase()}`;
}

function traceSource(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const delegatedBy = payloadValue(payload?.delegatedBy);
  if (delegatedBy) {
    return delegatedBy;
  }
  if (typeof payload?.hookName === 'string') {
    return formatLabel(payload.hookName);
  }
  return event.source;
}

function traceTarget(event: ActivityEvent, payload: Record<string, unknown> | null) {
  const agent = payloadValue(payload?.agent);
  if (agent) {
    return agent;
  }
  const toolName = payloadValue(payload?.toolName);
  if (toolName) {
    return toolName;
  }
  const delegatedAgents = payloadValue(payload?.delegatedAgents);
  if (delegatedAgents) {
    return delegatedAgents;
  }
  if (event.kind === 'APPROVAL_REQUESTED') {
    return payloadValue(payload?.requestedRole) || 'approval gate';
  }
  return event.source;
}

function traceHighlight(payload: Record<string, unknown> | null) {
  if (!payload) {
    return null;
  }
  return payloadValue(payload.focus)
    || payloadValue(payload.instruction)
    || payloadValue(payload.recommendation)
    || payloadValue(payload.approvalStatus)
    || null;
}

function timelineKeyFacts(payload: Record<string, unknown>) {
  const keys = ['agent', 'delegatedBy', 'toolName', 'requestedRole', 'focus', 'recommendation', 'approvalStatus'];
  return keys
    .map((key) => ({ label: formatLabel(key), raw: payload[key] }))
    .map(({ label, raw }) => ({ label, value: payloadValue(raw) }))
    .filter((entry): entry is { label: string; value: string } => Boolean(entry.value));
}

function payloadValue(value: unknown): string | null {
  if (value == null || value === '') {
    return null;
  }
  if (Array.isArray(value)) {
    return value.map((item) => formatValue(item)).join(' -> ');
  }
  return formatValue(value);
}

function hasPayloadFields(payload: Record<string, unknown>) {
  return orderedPayloadEntries(payload).length > 0;
}

function orderedPayloadEntries(payload: Record<string, unknown>) {
  const order = [
    'delegatedBy',
    'agent',
    'focus',
    'delegatedAgents',
    'recommendation',
    'approvalStatus',
    'requestedRole',
    'operatorId',
    'operatorRole',
    'instruction',
    'summary',
    'policyReference',
    'toolName',
    'location',
    'probe',
    'status',
    'content',
    'rawPayload',
    'shipmentEvidence',
    'escrowEvidence',
    'riskEvidence',
    'delegateSummaries',
  ];

  return Object.entries(payload)
    .filter(([key, value]) => key !== 'type' && value != null && value !== '')
    .sort(([left], [right]) => {
      const leftIndex = order.indexOf(left);
      const rightIndex = order.indexOf(right);
      if (leftIndex === -1 && rightIndex === -1) {
        return left.localeCompare(right);
      }
      if (leftIndex === -1) {
        return 1;
      }
      if (rightIndex === -1) {
        return -1;
      }
      return leftIndex - rightIndex;
    });
}

function formatValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map((item) => formatValue(item)).join(' -> ');
  }
  if (typeof value === 'string') {
    return formatLabel(value);
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function collapseDuplicateActivities(entries: ActivityTimelineEntry[]): DisplayActivityTimelineEntry[] {
  const grouped = new Map<string, DisplayActivityTimelineEntry>();

  for (const entry of entries) {
    const key = `${entry.event.kind}|${entry.event.source}|${entry.event.message}|${entry.event.structuredPayload}`;
    const existing = grouped.get(key);
    if (existing) {
      existing.duplicateCount += 1;
      continue;
    }
    grouped.set(key, {
      ...entry,
      duplicateCount: 1,
    });
  }

  return Array.from(grouped.values());
}

function buildActivityBlocks(entries: DisplayActivityTimelineEntry[]): ActivityBlock[] {
  const blocks: ActivityBlock[] = [];
  let current: ActivityBlock | null = null;

  for (const entry of entries) {
    if (startsNewBlock(entry, current)) {
      if (current) {
        blocks.push(current);
      }
      current = createActivityBlock(entry);
      if (current.phaseLabel === 'Workflow Update') {
        blocks.push(current);
        current = null;
      }
      continue;
    }

    if (current) {
      current.entries.push(entry);
      if (endsBlock(entry, current)) {
        blocks.push(current);
        current = null;
      }
      continue;
    }

    blocks.push(createActivityBlock(entry));
  }

  if (current) {
    blocks.push(current);
  }

  return blocks;
}

function startsNewBlock(entry: DisplayActivityTimelineEntry, current: ActivityBlock | null) {
  if (!current) {
    return true;
  }
  return entry.event.kind === 'DELEGATION_STARTED' || entry.event.kind === 'OPERATOR_INSTRUCTION_RECEIVED';
}

function endsBlock(entry: DisplayActivityTimelineEntry, current: ActivityBlock) {
  if (current.phaseLabel === 'Operator Turn') {
    return entry.event.kind === 'OPERATOR_REQUEST_COMPLETED';
  }
  if (current.phaseLabel === 'Case Intake') {
    return entry.event.kind === 'APPROVAL_REQUESTED';
  }
  return true;
}

function createActivityBlock(entry: DisplayActivityTimelineEntry): ActivityBlock {
  const payload = entry.payload;
  if (entry.event.kind === 'OPERATOR_INSTRUCTION_RECEIVED') {
    return {
      id: entry.event.eventId,
      phaseLabel: 'Operator Turn',
      title: payloadValue(payload?.instruction) || 'Operator follow-up',
      subtitle: 'workflow-agent received one operator request and delegated the necessary follow-up steps below.',
      entries: [entry],
    };
  }

  if (entry.event.kind === 'DELEGATION_STARTED') {
    return {
      id: entry.event.eventId,
      phaseLabel: 'Case Intake',
      title: payloadValue(payload?.instruction) || 'Initial case assessment',
      subtitle: 'This block covers the first investigation pass from intake through recommendation and approval gate setup.',
      entries: [entry],
    };
  }

  return {
    id: entry.event.eventId,
    phaseLabel: 'Workflow Update',
    title: friendlyKindLabel(entry.event.kind),
    subtitle: entry.event.message,
    entries: [entry],
  };
}

type ActivityTimelineEntry = {
  event: ActivityEvent;
  payload: Record<string, unknown> | null;
  category: ActivityCategory;
};

type DisplayActivityTimelineEntry = ActivityTimelineEntry & {
  duplicateCount: number;
};

type ActivityBlock = {
  id: string;
  phaseLabel: string;
  title: string;
  subtitle: string;
  entries: DisplayActivityTimelineEntry[];
};

export default App;