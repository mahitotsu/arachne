import { FormEvent, startTransition, useEffect, useMemo, useState } from 'react';
import {
  ActivityEvent,
  ApprovalSubmissionResult,
  CaseDetailView,
  CaseListItem,
  CaseType,
} from './types';

const API_BASE_URL = (import.meta.env.VITE_CASE_SERVICE_BASE_URL as string | undefined) ?? 'http://localhost:8080';

const CASE_TYPES: CaseType[] = [
  'ITEM_NOT_RECEIVED',
  'DELIVERED_BUT_DAMAGED',
  'HIGH_RISK_SETTLEMENT_HOLD',
  'SELLER_CANCELLATION_AFTER_AUTHORIZATION',
];

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
});

function App() {
  const [cases, setCases] = useState<CaseListItem[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState<string>('');
  const [selectedCase, setSelectedCase] = useState<CaseDetailView | null>(null);
  const [searchText, setSearchText] = useState('');
  const [searchDraft, setSearchDraft] = useState('');
  const [isLoadingCases, setIsLoadingCases] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [isSubmittingMessage, setIsSubmittingMessage] = useState(false);
  const [isSubmittingApproval, setIsSubmittingApproval] = useState(false);
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
  }, [searchText]);

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

  async function loadCases(preserveSelection = true) {
    setIsLoadingCases(true);
    try {
      const params = new URLSearchParams();
      if (searchText.trim()) {
        params.set('q', searchText.trim());
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
          <span>Case-service endpoint</span>
          <strong>{API_BASE_URL}</strong>
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
            <span className="status-chip">{isLoadingCases ? 'Refreshing' : `${cases.length} loaded`}</span>
          </div>

          <form className="search-form" onSubmit={(event) => {
            event.preventDefault();
            setSearchText(searchDraft);
          }}>
            <input
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              placeholder="Search by case id, order id, or type"
            />
            <button type="submit">Search</button>
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
                  <span className={`pill pill-${item.caseStatus.toLowerCase()}`}>{item.caseStatus.replaceAll('_', ' ')}</span>
                </div>
                <p>{item.caseId}</p>
                <p>Order {item.orderId}</p>
                <div className="case-card-bottom">
                  <span>{formatCurrency(item.amount, item.currency)}</span>
                  <span>{item.currentRecommendation.replaceAll('_', ' ')}</span>
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
                  <span className={`pill pill-${selectedCase.caseStatus.toLowerCase()}`}>{selectedCase.caseStatus.replaceAll('_', ' ')}</span>
                </div>
                <div className="summary-grid">
                  <SummaryField label="Case id" value={selectedCase.caseId} />
                  <SummaryField label="Order id" value={selectedCase.orderId} />
                  <SummaryField label="Transaction id" value={selectedCase.transactionId} />
                  <SummaryField label="Recommendation" value={selectedCase.currentRecommendation.replaceAll('_', ' ')} />
                  <SummaryField label="Amount" value={formatCurrency(selectedCase.amount, selectedCase.currency)} />
                  <SummaryField label="Approval status" value={selectedCase.approvalState.approvalStatus.replaceAll('_', ' ')} />
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
                  <h3>Chat</h3>
                  <span className="subtle-label">Operator continuation</span>
                </div>
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
                    {isSubmittingMessage ? 'Submitting...' : 'Send follow-up'}
                  </button>
                </form>
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
                  <span className="subtle-label">SSE-fed timeline</span>
                </div>
                <div className="timeline">
                  {selectedCase.activityHistory.map((event) => (
                    <article key={event.eventId} className="timeline-item">
                      <div className="timeline-meta">
                        <strong>{event.kind.replaceAll('_', ' ')}</strong>
                        <span>{event.source}</span>
                        <span>{formatDate(event.timestamp)}</span>
                      </div>
                      <p>{event.message}</p>
                    </article>
                  ))}
                  {!selectedCase.activityHistory.length && <div className="empty-state">No activity has been recorded for this case yet.</div>}
                </div>
              </section>
            </div>
          )}
        </section>
      </main>
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

function formatCurrency(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch {
    return `${currencyFormatter.format(amount)} ${currency}`;
  }
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

export default App;