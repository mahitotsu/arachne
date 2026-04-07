import type { ActivityEvent, CaseDetailView, CaseListItem } from '../../types';
import { buildCaseHeadline, formatCurrency, formatCurrencyContext, formatDate, formatLabel } from './formatters';

export type WorkspaceTab = 'action' | 'approval';

export interface BriefItem {
  id: string;
  text: string;
}

export interface ActionSummary {
  title: string;
  description: string;
  hints: string[];
  tab: WorkspaceTab;
}

export interface ParticipantSummary {
  name: string;
  role: string;
  status: string;
}

export interface ThreadTurn {
  id: string;
  speaker: string;
  role: string;
  lane: 'workflow' | 'specialist' | 'human' | 'system';
  timestamp: string;
  message: string;
  meta: string;
}

export interface ActivityCollaborationSummary {
  headline: string;
  notes: string[];
  tags: string[];
}

export function parseStructuredPayload(payload: string): Record<string, unknown> | null {
  if (!payload) {
    return null;
  }

  try {
    const parsed = JSON.parse(payload) as Record<string, unknown>;
    return parsed;
  } catch {
    return null;
  }
}

export function buildCaseSummary(detail: CaseDetailView | CaseListItem): string {
  if ('approvalState' in detail && detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL') {
    return 'Evidence gathering is done. Finance control needs to decide whether the recommendation can move forward.';
  }

  if (detail.caseStatus === 'COMPLETED') {
    return 'This case is complete. Use the activity and evidence tabs for audit or participant follow-up.';
  }

  if (detail.caseStatus === 'GATHERING_EVIDENCE') {
    return 'The workflow is still building a recommendation. Keep the central pane focused on evidence gaps and the next operator prompt.';
  }

  return 'This case is active. Start with the case state, then open the work pane only for the next step you need to take.';
}

export function buildUnderstandingBrief(detail: CaseDetailView): BriefItem[] {
  const items: BriefItem[] = [
    {
      id: 'status',
      text: `Current status is ${formatLabel(detail.caseStatus)} with recommendation ${formatLabel(detail.currentRecommendation)}.`,
    },
    {
      id: 'amount',
      text: `Financial exposure is ${formatCurrency(detail.amount, detail.currency)} on ${formatCurrencyContext(detail.currency)}.`,
    },
  ];

  if (detail.approvalState.approvalRequired) {
    items.push({
      id: 'approval',
      text: detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL'
        ? 'The workflow is waiting on finance control before settlement can proceed.'
        : `Approval state is ${formatLabel(detail.approvalState.approvalStatus)}.`,
    });
  }

  if (detail.outcome) {
    items.push({
      id: 'outcome',
      text: `Outcome recorded: ${formatLabel(detail.outcome.outcomeType)}. ${detail.outcome.summary}`,
    });
  }

  const latestActivity = detail.activityHistory[0];
  if (latestActivity) {
    items.push({
      id: 'latest-activity',
      text: `Latest activity at ${formatDate(latestActivity.timestamp)}: ${latestActivity.message}`,
    });
  }

  return items;
}

export function buildActionSummary(detail: CaseDetailView): ActionSummary {
  if (detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL') {
    return {
      title: 'Review evidence and submit a finance control decision',
      description: 'The case is blocked on approval. Use the Approval tab to approve or reject, and keep comments concise and audit-ready.',
      hints: ['Review evidence', 'Check policy reference', 'Submit approval'],
      tab: 'approval',
    };
  }

  if (detail.caseStatus === 'COMPLETED') {
    return {
      title: 'Review completed outcome and decide whether any follow-up is needed',
      description: 'The workflow is complete. The central pane is now for audit and outcome review, while the right rail is limited to any optional operator follow-up.',
      hints: ['Review outcome', 'Check approval record', 'Send follow-up only if needed'],
      tab: 'action',
    };
  }

  return {
    title: 'Clarify what the workflow should do next',
    description: 'Use Action to send a focused operator instruction. Avoid broad restatements and ask for one concrete next step.',
    hints: ['Send one instruction', 'Keep scope narrow', 'Refresh after agent response'],
    tab: 'action',
  };
}

export function buildParticipants(detail: CaseDetailView): ParticipantSummary[] {
  const seen = new Map<string, ParticipantSummary>();

  for (const event of detail.activityHistory) {
    const normalized = summarizeSource(event.source);
    if (!seen.has(normalized.name)) {
      seen.set(normalized.name, normalized);
    }
  }

  return Array.from(seen.values()).slice(0, 6);
}

export function summarizeSource(source: string): ParticipantSummary {
  const normalized = source.toLowerCase();

  if (normalized.includes('finance')) {
    return { name: 'Finance Control', role: 'Approver', status: 'Active' };
  }
  if (normalized.includes('shipment')) {
    return { name: 'Shipment Agent', role: 'Evidence', status: 'Reported' };
  }
  if (normalized.includes('escrow')) {
    return { name: 'Escrow Agent', role: 'Settlement', status: 'Reported' };
  }
  if (normalized.includes('risk')) {
    return { name: 'Risk Agent', role: 'Risk', status: 'Reported' };
  }
  if (normalized.includes('operator')) {
    return { name: 'Operator', role: 'Human', status: 'Active' };
  }
  if (normalized.includes('workflow')) {
    return { name: 'Workflow Agent', role: 'Coordinator', status: 'Active' };
  }

  return { name: formatLabel(source), role: 'System', status: 'Observed' };
}

export function buildConversationThread(detail: CaseDetailView): ThreadTurn[] {
  return [...detail.activityHistory]
    .sort((left, right) => left.timestamp.localeCompare(right.timestamp))
    .map((event) => {
      const summary = summarizeSource(event.source);
      const lane = classifyThreadLane(event.source);
      return {
        id: event.eventId,
        speaker: summary.name,
        role: summary.role,
        lane,
        timestamp: event.timestamp,
        message: event.message,
        meta: timelineSummary(event),
      };
    });
}

export function classifyThreadLane(source: string): ThreadTurn['lane'] {
  const normalized = source.toLowerCase();

  if (normalized.includes('workflow')) {
    return 'workflow';
  }
  if (normalized.includes('shipment') || normalized.includes('escrow') || normalized.includes('risk') || normalized.includes('specialist')) {
    return 'specialist';
  }
  if (normalized.includes('operator') || normalized.includes('finance')) {
    return 'human';
  }

  return 'system';
}

export function timelineSummary(event: ActivityEvent): string {
  const payload = parseStructuredPayload(event.structuredPayload);
  const delegate = typeof payload?.delegate === 'string' ? payload.delegate : undefined;
  const tool = typeof payload?.toolName === 'string' ? payload.toolName : undefined;

  if (delegate) {
    return `${formatLabel(event.kind)} via ${formatLabel(delegate)}`;
  }

  if (tool) {
    return `${formatLabel(event.kind)} via ${tool}`;
  }

  return formatLabel(event.kind);
}

export function describeActivityCollaboration(event: ActivityEvent): ActivityCollaborationSummary {
  const payload = parseStructuredPayload(event.structuredPayload);
  const source = summarizeSource(event.source);
  const kind = event.kind.toUpperCase();
  const delegate = typeof payload?.delegate === 'string' ? formatLabel(payload.delegate) : null;
  const tool = typeof payload?.toolName === 'string' ? payload.toolName : null;
  const tags = [source.name];

  if (delegate) {
    tags.push(delegate);
  }
  if (tool) {
    tags.push(tool);
  }

  if (kind === 'CASE_CREATED') {
    return {
      headline: 'The operator opened the case and started workflow handling.',
      notes: [
        'Case-service accepted the request and created the case projection.',
        'Workflow handling began immediately so downstream evidence gathering could start.',
      ],
      tags,
    };
  }

  if (kind === 'DELEGATION_STARTED') {
    return {
      headline: delegate
        ? `The workflow delegated the next step to ${delegate}.`
        : 'The workflow delegated work to another specialist boundary.',
      notes: [
        'This shows the coordinator agent handing work to a service-local specialist instead of handling everything in one prompt.',
        tool ? `The handoff was made through the ${tool} tool boundary.` : 'The handoff happened through an explicit backend capability boundary.',
      ],
      tags,
    };
  }

  if (kind === 'EVIDENCE_RECEIVED') {
    return {
      headline: `${source.name} returned evidence to the workflow.`,
      notes: [
        'The case projection was updated with structured evidence rather than a free-form transcript.',
        'This is one of the points where the operator can see cross-service collaboration changing the case state.',
      ],
      tags,
    };
  }

  if (kind === 'RECOMMENDATION_UPDATED') {
    return {
      headline: 'The workflow updated its recommendation based on the gathered evidence.',
      notes: [
        'The recommendation reflects the current combined view of shipment, escrow, risk, and policy inputs.',
        'This recommendation may still require human approval before settlement can continue.',
      ],
      tags,
    };
  }

  if (kind === 'APPROVAL_REQUESTED') {
    return {
      headline: 'The workflow paused and requested finance control approval.',
      notes: [
        'This is the interrupt boundary where human review becomes part of the workflow.',
        'Settlement cannot proceed until the approval path is resolved.',
      ],
      tags,
    };
  }

  if (kind === 'APPROVAL_SUBMITTED') {
    return {
      headline: 'Finance control submitted a decision back into the workflow.',
      notes: [
        'The decision re-enters the backend through the case-facing approval command.',
        'The workflow can now either resume settlement progression or return for more evidence.',
      ],
      tags,
    };
  }

  if (kind === 'SETTLEMENT_COMPLETED') {
    return {
      headline: 'A deterministic settlement action completed and the case outcome was recorded.',
      notes: [
        'This is where service-owned business logic finalizes the case result.',
        'The operator sees the outcome through the case projection rather than a direct call to the settlement service.',
      ],
      tags,
    };
  }

  if (kind === 'NOTIFICATION_DISPATCHED') {
    return {
      headline: 'Notification handling ran after the case decision was finalized.',
      notes: [
        'This shows that post-decision communication stays outside the settlement transaction boundary.',
      ],
      tags,
    };
  }

  if (kind === 'OPERATOR_INSTRUCTION_RECEIVED') {
    return {
      headline: 'A human operator provided the next instruction for the case.',
      notes: [
        'This step feeds new direction into the workflow without bypassing the backend orchestration boundary.',
      ],
      tags,
    };
  }

  return {
    headline: `${source.name} advanced the case workflow.`,
    notes: [
      timelineSummary(event),
      'This event was recorded in the durable case activity stream so the operator can understand how the case progressed.',
    ],
    tags,
  };
}

export function topTimeline(detail: CaseDetailView, limit = 5): ActivityEvent[] {
  return [...detail.activityHistory]
    .sort((left, right) => right.timestamp.localeCompare(left.timestamp))
    .slice(0, limit);
}

export function listHighlights(cases: CaseListItem[]): BriefItem[] {
  const pending = cases.filter((item) => item.pendingApproval).length;
  const completed = cases.filter((item) => item.caseStatus === 'COMPLETED').length;
  const active = cases.length - completed;

  return [
    { id: 'pending', text: `${pending} cases need finance control attention.` },
    { id: 'active', text: `${active} active cases are still moving through the workflow.` },
    { id: 'completed', text: `${completed} cases are already complete and available for audit only.` },
  ];
}

export function queueCounts(cases: CaseListItem[]): Array<{ label: string; value: string; tone: 'default' | 'strong' }> {
  return [
    { label: 'Open queue', value: String(cases.length), tone: 'default' },
    { label: 'Needs approval', value: String(cases.filter((item) => item.pendingApproval).length), tone: 'strong' },
    { label: 'Completed', value: String(cases.filter((item) => item.caseStatus === 'COMPLETED').length), tone: 'default' },
  ];
}

export function nextActionLabel(caseItem: CaseListItem | CaseDetailView): string {
  if ('approvalState' in caseItem && caseItem.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL') {
    return 'Submit approval decision';
  }
  if ('approvalStatus' in caseItem && caseItem.approvalStatus === 'PENDING_FINANCE_CONTROL') {
    return 'Submit approval decision';
  }
  if (caseItem.caseStatus === 'COMPLETED') {
    return 'Review outcome';
  }
  return 'Send operator instruction';
}

export function selectedCaseSubtitle(detail: CaseDetailView): string {
  return `${buildCaseHeadline(detail)}. ${buildCaseSummary(detail)}`;
}
