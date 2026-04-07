import { startTransition, useEffect, useMemo, useState } from 'react';
import { buildActionSummary, type WorkspaceTab } from '../lib/activity';
import { formatLabel } from '../lib/formatters';
import type { ActivityEvent, ApprovalSubmissionResult, CaseDetailView, CaseListItem } from '../../types';
import type { ApprovalForm, CreateCaseForm, DetailTab, Filters, MessageForm, ViewMode } from '../types';

interface PendingCreatePreview {
  caseType: CreateCaseForm['caseType'];
  orderId: string;
  amount: string;
  currency: string;
  initialMessage: string;
}

const API_BASE_URL = import.meta.env.VITE_CASE_SERVICE_BASE_URL ?? '';

const initialCreateForm: CreateCaseForm = {
  caseType: 'ITEM_NOT_RECEIVED',
  orderId: '',
  amount: '',
  currency: 'USD',
  initialMessage: '',
  operatorId: 'operator-1',
  operatorRole: 'CASE_OPERATOR',
};

const initialMessageForm: MessageForm = {
  message: '',
  operatorId: 'operator-1',
  operatorRole: 'CASE_OPERATOR',
};

const initialApprovalForm: ApprovalForm = {
  decision: 'APPROVE',
  comment: '',
  actorId: 'finance-1',
  actorRole: 'FINANCE_CONTROL',
};

export function useOperatorConsole() {
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [cases, setCases] = useState<CaseListItem[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState('');
  const [selectedCase, setSelectedCase] = useState<CaseDetailView | null>(null);
  const [pendingCreatePreview, setPendingCreatePreview] = useState<PendingCreatePreview | null>(null);
  const [workspaceTab, setWorkspaceTab] = useState<WorkspaceTab>('action');
  const [detailTab, setDetailTab] = useState<DetailTab>('summary');
  const [filters, setFilters] = useState<Filters>({ q: '', caseStatus: '', caseType: '' });
  const [isLoadingCases, setIsLoadingCases] = useState(false);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [isSubmittingMessage, setIsSubmittingMessage] = useState(false);
  const [isSubmittingApproval, setIsSubmittingApproval] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [flashMessage, setFlashMessage] = useState('');
  const [createForm, setCreateForm] = useState<CreateCaseForm>(initialCreateForm);
  const [messageForm, setMessageForm] = useState<MessageForm>(initialMessageForm);
  const [approvalForm, setApprovalForm] = useState<ApprovalForm>(initialApprovalForm);

  useEffect(() => {
    void loadCases();
  }, []);

  useEffect(() => {
    if (viewMode === 'detail' && selectedCaseId) {
      void loadCaseDetail(selectedCaseId, true);
    }
  }, [selectedCaseId, viewMode]);

  useEffect(() => {
    if (!selectedCaseId) {
      return undefined;
    }

    const source = new EventSource(`${API_BASE_URL}/api/cases/${selectedCaseId}/activity-stream`);
    const refresh = () => {
      void loadCaseDetail(selectedCaseId, false);
      void loadCases(false);
    };

    source.onmessage = refresh;
    source.addEventListener('activity', refresh as EventListener);
    source.onerror = () => {
      source.close();
    };

    return () => {
      source.close();
    };
  }, [selectedCaseId]);

  const selectedListCase = useMemo(
    () => cases.find((item) => item.caseId === selectedCaseId) ?? cases[0] ?? null,
    [cases, selectedCaseId],
  );

  const selectedCaseDetail = useMemo(
    () => selectedCase?.caseId === selectedCaseId ? selectedCase : null,
    [selectedCase, selectedCaseId],
  );

  const primaryAction = useMemo(() => {
    if (!selectedCaseDetail) {
      return null;
    }

    return buildActionSummary(selectedCaseDetail);
  }, [selectedCaseDetail]);

  function selectCase(caseId: string) {
    startTransition(() => {
      setPendingCreatePreview(null);
      setSelectedCaseId(caseId);
      setSelectedCase((current) => current?.caseId === caseId ? current : null);
    });
  }

  async function loadCases(showBusy = true, nextFilters: Filters = filters) {
    if (showBusy) {
      setIsLoadingCases(true);
    }

    try {
      const params = new URLSearchParams();
      if (nextFilters.q.trim()) {
        params.set('q', nextFilters.q.trim());
      }
      if (nextFilters.caseStatus) {
        params.set('caseStatus', nextFilters.caseStatus);
      }
      if (nextFilters.caseType) {
        params.set('caseType', nextFilters.caseType);
      }

      const query = params.toString();
      const response = await fetch(`${API_BASE_URL}/api/cases${query ? `?${query}` : ''}`);
      if (!response.ok) {
        throw new Error(`Case list request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseListItem[];
      setCases(payload);
      if (!selectedCaseId && payload[0]) {
        setSelectedCaseId(payload[0].caseId);
      }
      if (selectedCaseId && payload.every((item) => item.caseId !== selectedCaseId)) {
        setSelectedCaseId(payload[0]?.caseId ?? '');
        if (!payload[0]) {
          setSelectedCase(null);
          setViewMode('list');
        }
      }
      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to load cases.');
    } finally {
      if (showBusy) {
        setIsLoadingCases(false);
      }
    }
  }

  async function loadCaseDetail(caseId: string, showBusy: boolean) {
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
      if (payload.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' && workspaceTab === 'action') {
        setWorkspaceTab('approval');
      }
      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to load case detail.');
    } finally {
      if (showBusy) {
        setIsLoadingDetail(false);
      }
    }
  }

  function openCase(caseId: string) {
    startTransition(() => {
      setPendingCreatePreview(null);
      setSelectedCaseId(caseId);
      setSelectedCase((current) => current?.caseId === caseId ? current : null);
      setViewMode('detail');
    });
  }

  function optimisticEvent(message: string, source: string, kind: string): ActivityEvent {
    return {
      eventId: `optimistic-${Date.now()}`,
      caseId: selectedCaseId,
      timestamp: new Date().toISOString(),
      kind,
      source,
      message,
      structuredPayload: '',
    };
  }

  async function createCase() {
    setIsSubmittingCreate(true);
    setPendingCreatePreview({
      caseType: createForm.caseType,
      orderId: createForm.orderId,
      amount: createForm.amount,
      currency: createForm.currency,
      initialMessage: createForm.initialMessage,
    });
    setSelectedCase(null);
    setSelectedCaseId('');
    setViewMode('detail');

    try {
      const response = await fetch(`${API_BASE_URL}/api/cases`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          caseType: createForm.caseType,
          orderId: createForm.orderId,
          amount: Number(createForm.amount),
          currency: createForm.currency,
          initialMessage: createForm.initialMessage,
          operatorId: createForm.operatorId,
          operatorRole: createForm.operatorRole,
        }),
      });

      if (!response.ok) {
        throw new Error(`Create case request failed with status ${response.status}.`);
      }

      const payload = (await response.json()) as CaseDetailView;
      setPendingCreatePreview(null);
      setSelectedCase(payload);
      setSelectedCaseId(payload.caseId);
      setIsCreateOpen(false);
      setCreateForm(initialCreateForm);
      setWorkspaceTab(buildActionSummary(payload).tab);
      setFlashMessage(`Created ${formatLabel(payload.caseType)} case ${payload.caseId}.`);
      setErrorMessage('');
      await loadCases(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to create case.');
    } finally {
      setIsSubmittingCreate(false);
    }
  }

  async function submitMessage() {
    if (!selectedCaseId || !messageForm.message.trim()) {
      return;
    }

    setIsSubmittingMessage(true);
    setSelectedCase((current) => current ? {
      ...current,
      activityHistory: [
        optimisticEvent(
          messageForm.message.trim(),
          messageForm.operatorRole === 'FINANCE_CONTROL' ? 'finance-control' : 'case-operator',
          'OPERATOR_INSTRUCTION_RECEIVED',
        ),
        ...current.activityHistory,
      ],
    } : current);

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
      setMessageForm((current) => ({ ...current, message: '' }));
      setWorkspaceTab(buildActionSummary(payload).tab);
      setFlashMessage('Operator message submitted.');
      setErrorMessage('');
      await loadCases(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to add message.');
      await loadCaseDetail(selectedCaseId, false);
    } finally {
      setIsSubmittingMessage(false);
    }
  }

  async function submitApproval() {
    if (!selectedCaseId) {
      return;
    }

    setIsSubmittingApproval(true);
    setSelectedCase((current) => current ? {
      ...current,
      activityHistory: [
        optimisticEvent(`${approvalForm.decision} submitted by finance control.`, 'finance-control', 'APPROVAL_SUBMITTED'),
        ...current.activityHistory,
      ],
    } : current);

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
      await loadCases(false);
      await loadCaseDetail(selectedCaseId, false);
      setWorkspaceTab(payload.resumeAccepted ? 'action' : 'approval');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to submit approval.');
      await loadCaseDetail(selectedCaseId, false);
    } finally {
      setIsSubmittingApproval(false);
    }
  }

  function refreshWithFilters(patch: Partial<Filters>) {
    const next = { ...filters, ...patch };
    setFilters(next);
    void loadCases(true, next);
  }

  return {
    state: {
      viewMode,
      cases,
      selectedCaseId,
      selectedCase,
      selectedCaseDetail,
      selectedListCase,
      pendingCreatePreview,
      workspaceTab,
      detailTab,
      filters,
      isLoadingCases,
      isLoadingDetail,
      isSubmittingCreate,
      isSubmittingMessage,
      isSubmittingApproval,
      isCreateOpen,
      errorMessage,
      flashMessage,
      createForm,
      messageForm,
      approvalForm,
      primaryAction,
    },
    actions: {
      setViewMode,
      openCase,
      selectCase,
      setWorkspaceTab,
      setDetailTab,
      setIsCreateOpen,
      clearFlashMessage: () => setFlashMessage(''),
      setCreateForm,
      setMessageForm,
      setApprovalForm,
      refreshCases: () => void loadCases(),
      refreshWithFilters,
      createCase,
      submitMessage,
      submitApproval,
    },
  };
}
