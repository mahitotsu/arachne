export type CaseStatus =
  | 'OPEN'
  | 'GATHERING_EVIDENCE'
  | 'AWAITING_APPROVAL'
  | 'READY_FOR_SETTLEMENT'
  | 'COMPLETED';

export type Recommendation = 'REFUND' | 'CONTINUED_HOLD' | 'PENDING_MORE_EVIDENCE';

export type ApprovalStatus = 'NOT_REQUIRED' | 'PENDING_FINANCE_CONTROL' | 'APPROVED' | 'REJECTED';

export type OutcomeType = 'REFUND_EXECUTED' | 'CONTINUED_HOLD_RECORDED';

export type CaseType =
  | 'ITEM_NOT_RECEIVED'
  | 'DELIVERED_BUT_DAMAGED'
  | 'HIGH_RISK_SETTLEMENT_HOLD'
  | 'SELLER_CANCELLATION_AFTER_AUTHORIZATION';

export interface CaseListItem {
  caseId: string;
  caseType: string;
  caseStatus: CaseStatus;
  orderId: string;
  amount: number;
  currency: string;
  currentRecommendation: Recommendation;
  pendingApproval: boolean;
  updatedAt: string;
}

export interface CaseSummaryView {
  caseId: string;
  caseType: string;
  caseStatus: CaseStatus;
  orderId: string;
  transactionId: string;
  amount: number;
  currency: string;
  currentRecommendation: Recommendation;
}

export interface EvidenceView {
  shipmentEvidence: string;
  escrowEvidence: string;
  riskEvidence: string;
  policyReference: string;
}

export interface ActivityEvent {
  eventId: string;
  caseId: string;
  timestamp: string;
  kind: string;
  source: string;
  message: string;
  structuredPayload: string;
}

export interface ApprovalStateView {
  approvalRequired: boolean;
  approvalStatus: ApprovalStatus;
  requestedRole: string | null;
  requestedAt: string | null;
  decisionAt: string | null;
  decisionBy: string | null;
  comment: string | null;
}

export interface OutcomeView {
  outcomeType: OutcomeType;
  outcomeStatus: string;
  settledAt: string;
  settlementReference: string;
  summary: string;
}

export interface CaseDetailView {
  caseId: string;
  caseType: string;
  caseStatus: CaseStatus;
  orderId: string;
  transactionId: string;
  amount: number;
  currency: string;
  currentRecommendation: Recommendation;
  caseSummary: CaseSummaryView;
  evidence: EvidenceView;
  activityHistory: ActivityEvent[];
  approvalState: ApprovalStateView;
  outcome: OutcomeView | null;
}

export interface ApprovalSubmissionResult {
  caseId: string;
  approvalState: ApprovalStateView;
  workflowStatus: CaseStatus;
  resumeAccepted: boolean;
  message: string;
}