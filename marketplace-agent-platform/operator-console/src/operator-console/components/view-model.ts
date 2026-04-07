import type { WorkspaceTab } from '../lib/activity';
import type { DetailTab } from '../types';
import type { CaseDetailView, CaseListItem, CaseType } from '../../types';

export interface DemoCasePreset {
  id: string;
  label: string;
  description: string;
  values: {
    caseType: CaseType;
    orderId: string;
    amount: string;
    currency: string;
    initialMessage: string;
    operatorId: string;
    operatorRole: string;
  };
}

export const CASE_TYPE_OPTIONS: CaseType[] = [
  'ITEM_NOT_RECEIVED',
  'DELIVERED_BUT_DAMAGED',
  'HIGH_RISK_SETTLEMENT_HOLD',
  'SELLER_CANCELLATION_AFTER_AUTHORIZATION',
];

export const CASE_STATUS_OPTIONS = [
  'OPEN',
  'GATHERING_EVIDENCE',
  'AWAITING_APPROVAL',
  'READY_FOR_SETTLEMENT',
  'COMPLETED',
] as const;

export const detailTabs: Array<{ value: DetailTab; label: string }> = [
  { value: 'summary', label: 'Case Summary' },
  { value: 'evidence', label: 'Evidence' },
  { value: 'activity', label: 'Activity History' },
  { value: 'outcome', label: 'Approval And Outcome' },
];

export const workspaceTabs: Array<{ value: WorkspaceTab; label: string }> = [
  { value: 'action', label: 'Agent Request' },
  { value: 'approval', label: 'Approval' },
];

export const demoCasePresets: DemoCasePreset[] = [
  {
    id: 'bedrock-demo-approve',
    label: 'Bedrock Demo 1: Approval Path',
    description: 'Live demo input from BEDROCK-DEMO-REPORT for the first interrupt-and-approve run.',
    values: {
      caseType: 'ITEM_NOT_RECEIVED',
      orderId: 'ord-demo-001',
      amount: '199.99',
      currency: 'USD',
      initialMessage: 'Buyer reports the item never arrived. Seller claims it was shipped.',
      operatorId: 'operator-1',
      operatorRole: 'CASE_OPERATOR',
    },
  },
  {
    id: 'bedrock-demo-approved',
    label: 'Bedrock Demo 2: Approved Variant',
    description: 'Live demo input from BEDROCK-DEMO-REPORT for the second approved-completion run.',
    values: {
      caseType: 'ITEM_NOT_RECEIVED',
      orderId: 'ord-demo-002',
      amount: '249.50',
      currency: 'USD',
      initialMessage: 'Buyer reports package missing and requests refund.',
      operatorId: 'operator-2',
      operatorRole: 'CASE_OPERATOR',
    },
  },
  {
    id: 'delivered-damaged',
    label: 'Scenario: Delivered But Damaged',
    description: 'Uses the documented delivered-damage shipment scenario input.',
    values: {
      caseType: 'DELIVERED_BUT_DAMAGED',
      orderId: 'order-dmg-1',
      amount: '89.90',
      currency: 'USD',
      initialMessage: 'Buyer says the package arrived crushed and wet, and requests review of the damage claim.',
      operatorId: 'operator-1',
      operatorRole: 'CASE_OPERATOR',
    },
  },
  {
    id: 'high-risk-hold',
    label: 'Scenario: High Risk Settlement Hold',
    description: 'Uses the verified risk-review scenario input for a high-risk settlement hold.',
    values: {
      caseType: 'HIGH_RISK_SETTLEMENT_HOLD',
      orderId: 'order-risk-1',
      amount: '1499.00',
      currency: 'USD',
      initialMessage: 'Risk controls flagged unusual account activity and require the settlement hold to remain in place pending review.',
      operatorId: 'operator-1',
      operatorRole: 'CASE_OPERATOR',
    },
  },
];

export type DetailShell =
  | { kind: 'detail'; detail: CaseDetailView }
  | { kind: 'list-preview'; item: CaseListItem }
  | {
      kind: 'create-preview';
      preview: {
        caseType: CaseType;
        orderId: string;
        amount: string;
        currency: string;
        initialMessage: string;
      };
    };
