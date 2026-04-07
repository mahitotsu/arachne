import type { CaseType } from '../types';

export type ViewMode = 'list' | 'detail';

export interface Filters {
  q: string;
  caseStatus: string;
  caseType: string;
}

export interface CreateCaseForm {
  caseType: CaseType;
  orderId: string;
  amount: string;
  currency: string;
  initialMessage: string;
  operatorId: string;
  operatorRole: string;
}

export interface MessageForm {
  message: string;
  operatorId: string;
  operatorRole: string;
}

export interface ApprovalForm {
  decision: string;
  comment: string;
  actorId: string;
  actorRole: string;
}

export type DetailTab = 'summary' | 'evidence' | 'activity' | 'outcome';
