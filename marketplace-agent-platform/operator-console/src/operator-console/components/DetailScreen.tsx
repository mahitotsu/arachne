import { useMemo, useState, type FormEvent, type SyntheticEvent } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import {
  buildActionSummary,
  buildCaseSummary,
  buildParticipants,
  buildUnderstandingBrief,
  describeActivityCollaboration,
  summarizeSource,
  timelineSummary,
  type WorkspaceTab,
} from '../lib/activity';
import { buildCaseHeadline, formatCurrency, formatDate, formatLabel, formatRelativeTime } from '../lib/formatters';
import type { DetailTab } from '../types';
import type { ActivityEvent, CaseDetailView, CaseListItem } from '../../types';
import { detailTabs, type DetailShell, workspaceTabs } from './view-model';
import { EmptyState, EvidenceBlock, KeyValueCard, LoadingPlaceholder, PreviewNarrative, StatusChip, SummaryMetric } from './common';

const panelHeight = {
  xs: 'auto',
  lg: 'calc(100vh - 248px)',
};

interface ActivityThread {
  id: string;
  title: string;
  subtitle: string;
  events: ActivityEvent[];
  tone: 'operator' | 'approval' | 'workflow' | 'system';
}

interface RequestTemplate {
  id: string;
  label: string;
  description: string;
  body: string;
}

export interface DetailScreenProps {
  detailShell: DetailShell | null;
  selectedCaseId: string;
  isLoadingDetail: boolean;
  workspaceTab: WorkspaceTab;
  detailTab: DetailTab;
  messageForm: { message: string; operatorId: string; operatorRole: string };
  approvalForm: { decision: string; comment: string; actorId: string; actorRole: string };
  isSubmittingCreate: boolean;
  isSubmittingMessage: boolean;
  isSubmittingApproval: boolean;
  onChangeDetailTab: (tab: DetailTab) => void;
  onChangeWorkspaceTab: (tab: WorkspaceTab) => void;
  onRefresh: () => void;
  onMessageChange: (patch: Partial<{ message: string; operatorId: string; operatorRole: string }>) => void;
  onApprovalChange: (patch: Partial<{ decision: string; comment: string; actorId: string; actorRole: string }>) => void;
  onSubmitMessage: (event: FormEvent<HTMLFormElement>) => void;
  onSubmitApproval: (event: FormEvent<HTMLFormElement>) => void;
}

export function DetailScreen({
  detailShell,
  selectedCaseId,
  isLoadingDetail,
  workspaceTab,
  detailTab,
  messageForm,
  approvalForm,
  isSubmittingCreate,
  isSubmittingMessage,
  isSubmittingApproval,
  onChangeDetailTab,
  onChangeWorkspaceTab,
  onRefresh,
  onMessageChange,
  onApprovalChange,
  onSubmitMessage,
  onSubmitApproval,
}: DetailScreenProps) {
  const [expandedThreadIds, setExpandedThreadIds] = useState<string[]>([]);
  const detail = detailShell?.kind === 'detail' ? detailShell.detail : null;
  const quickSummary = detail
    ? buildCaseSummary(detail)
    : detailShell?.kind === 'list-preview'
      ? `The detail workspace opened immediately for ${buildCaseHeadline(detailShell.item)}. Additional evidence and activity history are still loading.`
      : detailShell?.kind === 'create-preview'
        ? 'The new case is being created now. The workspace is already open so the operator does not wait on backend processing before orienting themselves.'
        : '';
  const actionSummary = detail ? buildActionSummary(detail) : null;
  const activityThreads = useMemo(() => detail ? buildActivityThreads(detail.activityHistory) : [], [detail]);
  const requestTemplates = useMemo(() => buildRequestTemplates(detailShell), [detailShell]);
  const visibleExpandedThreadIds = expandedThreadIds.filter((threadId) => activityThreads.some((thread) => thread.id === threadId));

  if (!detailShell) {
    return (
      <Paper sx={{ p: 4 }}>
        <EmptyState title="No case selected" body="Open a case from the list screen to inspect detail and perform actions." />
      </Paper>
    );
  }

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', xl: 'minmax(0, 1fr) 360px' }, gap: 3, alignItems: 'start' }}>
      <Box sx={{ display: 'grid', gap: 3 }}>
        <Paper sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ sm: 'center' }}>
              <Box>
                <Typography variant="overline" color="text.secondary">
                  Case detail
                </Typography>
                <Typography variant="h4">
                  {detailShell.kind === 'detail'
                    ? buildCaseHeadline(detailShell.detail)
                    : detailShell.kind === 'list-preview'
                      ? buildCaseHeadline(detailShell.item)
                      : `${formatLabel(detailShell.preview.caseType)} for ${detailShell.preview.orderId || 'pending order id'}`}
                </Typography>
              </Box>

              <Button variant="outlined" onClick={onRefresh}>
                Refresh
              </Button>
            </Stack>

            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              {detailShell.kind === 'detail' ? (
                <>
                  <Chip label={detailShell.detail.caseId} variant="outlined" />
                  <StatusChip label={formatLabel(detailShell.detail.caseStatus)} tone="status" />
                  <StatusChip label={formatLabel(detailShell.detail.currentRecommendation)} tone="recommendation" />
                  <StatusChip
                    label={formatLabel(detailShell.detail.approvalState.approvalStatus)}
                    tone={detailShell.detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' ? 'warning' : 'neutral'}
                  />
                </>
              ) : detailShell.kind === 'list-preview' ? (
                <>
                  <Chip label={detailShell.item.caseId} variant="outlined" />
                  <StatusChip label={formatLabel(detailShell.item.caseStatus)} tone="status" />
                  <StatusChip label={formatLabel(detailShell.item.currentRecommendation)} tone="recommendation" />
                  <StatusChip label={formatLabel(detailShell.item.approvalStatus)} tone={detailShell.item.pendingApproval ? 'warning' : 'neutral'} />
                </>
              ) : (
                <>
                  <Chip label="Creating case" variant="outlined" />
                  <StatusChip label={formatLabel(detailShell.preview.caseType)} tone="neutral" />
                  <StatusChip label="Pending response" tone="warning" />
                </>
              )}
            </Stack>

            <Typography color="text.secondary">{quickSummary}</Typography>

            {(isLoadingDetail || isSubmittingCreate) ? (
              <Alert severity="info">
                The detail layout is already visible. Additional case data will appear here as soon as the backend response arrives.
              </Alert>
            ) : null}
          </Stack>
        </Paper>

        <Paper sx={{ overflow: 'hidden', minHeight: panelHeight, height: panelHeight, display: 'flex', flexDirection: 'column' }}>
          <Tabs
            value={detailTab}
            onChange={(_: SyntheticEvent, value: DetailTab) => onChangeDetailTab(value)}
            variant="scrollable"
            scrollButtons="auto"
            sx={{ px: 2, borderBottom: '1px solid', borderColor: 'divider', flexShrink: 0 }}
          >
            {detailTabs.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} />
            ))}
          </Tabs>

          <Box sx={{ p: 3, overflowY: { xs: 'visible', lg: 'auto' }, flex: 1, minHeight: 0 }}>
            <DetailProjectionPanel
              detailShell={detailShell}
              detailTab={detailTab}
              isLoadingDetail={isLoadingDetail || isSubmittingCreate}
              activityThreads={activityThreads}
              expandedThreadIds={visibleExpandedThreadIds}
              onExpandedThreadIdsChange={setExpandedThreadIds}
            />
          </Box>
        </Paper>
      </Box>

      <Stack spacing={3} alignSelf="start">
        <Paper sx={{ p: 3, backgroundColor: alpha('#245b8f', 0.04) }}>
          <Stack spacing={1.5}>
            <Typography variant="overline" color="text.secondary">
              Control surface
            </Typography>
            <Typography variant="h6">Human command rail</Typography>
            <Typography variant="body2" color="text.secondary">
              The center column is the durable case projection from case-service. Use this rail only to send the next operator request or a finance approval decision.
            </Typography>
          </Stack>
        </Paper>

        <Paper sx={{ overflow: 'hidden', minHeight: panelHeight, height: panelHeight, display: 'flex', flexDirection: 'column' }}>
          <Tabs
            value={workspaceTab}
            onChange={(_: SyntheticEvent, value: WorkspaceTab) => onChangeWorkspaceTab(value)}
            variant="fullWidth"
            sx={{ px: 2, borderBottom: '1px solid', borderColor: 'divider', flexShrink: 0 }}
          >
            {workspaceTabs.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} />
            ))}
          </Tabs>

          <Box sx={{ p: 3, overflowY: { xs: 'visible', lg: 'auto' }, flex: 1, minHeight: 0 }}>
            <WorkspacePanel
              detailShell={detailShell}
              workspaceTab={workspaceTab}
              selectedCaseId={selectedCaseId}
              messageForm={messageForm}
              approvalForm={approvalForm}
              actionSummary={actionSummary}
              requestTemplates={requestTemplates}
              isSubmittingMessage={isSubmittingMessage}
              isSubmittingApproval={isSubmittingApproval}
              onMessageChange={onMessageChange}
              onApprovalChange={onApprovalChange}
              onSubmitMessage={onSubmitMessage}
              onSubmitApproval={onSubmitApproval}
            />
          </Box>
        </Paper>
      </Stack>
    </Box>
  );
}

function DetailProjectionPanel({
  detailShell,
  detailTab,
  isLoadingDetail,
  activityThreads,
  expandedThreadIds,
  onExpandedThreadIdsChange,
}: {
  detailShell: DetailShell;
  detailTab: DetailTab;
  isLoadingDetail: boolean;
  activityThreads: ActivityThread[];
  expandedThreadIds: string[];
  onExpandedThreadIdsChange: (next: string[]) => void;
}) {
  const detail = detailShell.kind === 'detail' ? detailShell.detail : null;

  if (detailTab === 'summary') {
    return detail ? <SummaryProjection detail={detail} /> : <SummaryPreview detailShell={detailShell} />;
  }

  if (detailTab === 'evidence') {
    return detail ? <EvidenceProjection detail={detail} /> : <LoadingPlaceholder body="Evidence fields will appear here once the backend finishes loading the detailed case record." active={isLoadingDetail} />;
  }

  if (detailTab === 'activity') {
    return detail
      ? (
        <ActivityProjection
          detail={detail}
          threads={activityThreads}
          expandedThreadIds={expandedThreadIds}
          onExpandedThreadIdsChange={onExpandedThreadIdsChange}
        />
      )
      : detailShell.kind === 'create-preview' && detailShell.preview.initialMessage.trim()
        ? (
          <Paper variant="outlined" sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" gutterBottom>
              Initial operator message
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {detailShell.preview.initialMessage}
            </Typography>
          </Paper>
        )
        : <LoadingPlaceholder body="Activity history will appear here once the case detail payload arrives." active={isLoadingDetail} />;
  }

  return detail
    ? <OutcomeProjection detail={detail} />
    : <LoadingPlaceholder body="Approval and outcome details will appear here once the case detail payload arrives." active={isLoadingDetail} />;
}

function SummaryProjection({ detail }: { detail: CaseDetailView }) {
  const brief = buildUnderstandingBrief(detail);
  const participants = buildParticipants(detail);

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <SummaryMetric label="Status" value={formatLabel(detail.caseStatus)} tone="primary" />
        <SummaryMetric label="Recommendation" value={formatLabel(detail.currentRecommendation)} tone="neutral" />
        <SummaryMetric label="Amount" value={formatCurrency(detail.amount, detail.currency)} tone="neutral" />
        <SummaryMetric label="Approval" value={formatLabel(detail.approvalState.approvalStatus)} tone={detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' ? 'warning' : 'success'} />
      </Stack>

      <Paper variant="outlined" sx={{ p: 2.5, backgroundColor: alpha('#245b8f', 0.03) }}>
        <Typography variant="subtitle1" gutterBottom>
          Current understanding
        </Typography>
        <Stack spacing={1.25}>
          {brief.map((item) => (
            <Typography key={item.id} variant="body2" color="text.secondary">
              {item.text}
            </Typography>
          ))}
        </Stack>
      </Paper>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <KeyValueCard
          title="Case identifiers"
          items={[
            ['Case ID', detail.caseId],
            ['Order ID', detail.orderId],
            ['Transaction ID', detail.transactionId],
            ['Case type', formatLabel(detail.caseType)],
          ]}
        />
        <KeyValueCard
          title="Current control point"
          items={[
            ['Workflow state', formatLabel(detail.caseStatus)],
            ['Approval state', formatLabel(detail.approvalState.approvalStatus)],
            ['Recommendation', formatLabel(detail.currentRecommendation)],
            ['Current focus', buildCaseSummary(detail)],
          ]}
        />
      </Stack>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Typography variant="subtitle1" gutterBottom>
          Active services and human actors
        </Typography>
        <Stack spacing={1.5}>
          {participants.map((participant) => (
            <Stack key={`${participant.name}-${participant.role}`} direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
              <Box>
                <Typography variant="body2">{participant.name}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {participant.role}
                </Typography>
              </Box>
              <StatusChip label={participant.status} tone="neutral" />
            </Stack>
          ))}
        </Stack>
      </Paper>
    </Stack>
  );
}

function SummaryPreview({ detailShell }: { detailShell: DetailShell }) {
  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <SummaryMetric label="Case type" value={detailShell.kind === 'create-preview' ? formatLabel(detailShell.preview.caseType) : 'Loading'} tone="neutral" />
        <SummaryMetric label="Order" value={detailShell.kind === 'list-preview' ? detailShell.item.orderId : detailShell.kind === 'create-preview' ? detailShell.preview.orderId || 'Pending' : 'Loading'} tone="neutral" />
        <SummaryMetric label="State" value="Loading" tone="warning" />
      </Stack>
      <PreviewNarrative detailShell={detailShell} />
    </Stack>
  );
}

function EvidenceProjection({ detail }: { detail: CaseDetailView }) {
  return (
    <Stack spacing={2}>
      <EvidenceBlock title="Shipment evidence" value={detail.evidence.shipmentEvidence} />
      <EvidenceBlock title="Escrow evidence" value={detail.evidence.escrowEvidence} />
      <EvidenceBlock title="Risk evidence" value={detail.evidence.riskEvidence} />
      <EvidenceBlock title="Policy reference" value={detail.evidence.policyReference} />
    </Stack>
  );
}

function ActivityProjection({
  detail,
  threads,
  expandedThreadIds,
  onExpandedThreadIdsChange,
}: {
  detail: CaseDetailView;
  threads: ActivityThread[];
  expandedThreadIds: string[];
  onExpandedThreadIdsChange: (next: string[]) => void;
}) {
  if (!threads.length) {
    return <EmptyState title="No activity yet" body="Activity history will appear here as the workflow records case-facing events." />;
  }

  const allExpanded = expandedThreadIds.length === threads.length;

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1.5}>
        <Box>
          <Typography variant="subtitle1">Activity history</Typography>
          <Typography variant="body2" color="text.secondary">
            Events are grouped as operator-facing threads so you can read one request-and-response sequence at a time.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={() => onExpandedThreadIdsChange(threads.map((thread) => thread.id))} disabled={allExpanded}>
            Expand all
          </Button>
          <Button size="small" onClick={() => onExpandedThreadIdsChange([])} disabled={!expandedThreadIds.length}>
            Collapse all
          </Button>
        </Stack>
      </Stack>

      <Stack spacing={1.5}>
        {threads.map((thread) => {
          const expanded = expandedThreadIds.includes(thread.id);
          return (
            <Accordion
              key={thread.id}
              expanded={expanded}
              onChange={(_, nextExpanded) => {
                onExpandedThreadIdsChange(
                  nextExpanded
                    ? [...expandedThreadIds, thread.id]
                    : expandedThreadIds.filter((threadId) => threadId !== thread.id),
                );
              }}
              disableGutters
              sx={{
                border: '1px solid',
                borderColor: alpha(threadToneColor(thread.tone), 0.24),
                borderRadius: 2,
                overflow: 'hidden',
                '&::before': { display: 'none' },
              }}
            >
              <AccordionSummary expandIcon={<Typography color="text.secondary">+</Typography>} sx={{ px: 2.5, py: 1.5, backgroundColor: alpha(threadToneColor(thread.tone), 0.05) }}>
                <Stack spacing={1} sx={{ width: '100%', minWidth: 0 }}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                      <StatusChip label={threadToneLabel(thread.tone)} tone={threadToneChip(thread.tone)} />
                      <Typography variant="subtitle2">{thread.title}</Typography>
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      {thread.subtitle}
                    </Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {thread.events[0]?.message}
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ px: 2.5, py: 2 }}>
                <Stack spacing={2}>
                  {thread.events.map((event, index) => (
                    <ActivityMessageRow key={event.eventId} event={event} isReply={index > 0} />
                  ))}
                </Stack>
              </AccordionDetails>
            </Accordion>
          );
        })}
      </Stack>

      <Typography variant="caption" color="text.secondary">
        {detail.activityHistory.length} events recorded for this case.
      </Typography>
    </Stack>
  );
}

function ActivityMessageRow({ event, isReply }: { event: ActivityEvent; isReply: boolean }) {
  const tone = eventKindTone(event.kind);
  const source = summarizeSource(event.source);
  const collaboration = describeActivityCollaboration(event);

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '24px minmax(0, 1fr)', gap: 1.5, alignItems: 'start' }}>
      <Box sx={{ display: 'flex', justifyContent: 'center', pt: 0.75 }}>
        <Box sx={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: threadToneColor(tone), boxShadow: `0 0 0 4px ${alpha(threadToneColor(tone), 0.14)}` }} />
      </Box>
      <Box>
        {isReply ? <Box sx={{ ml: 0.5, mb: 1, width: 2, height: 10, backgroundColor: alpha(threadToneColor(tone), 0.22) }} /> : null}
        <Paper variant="outlined" sx={{ p: 2, borderColor: alpha(threadToneColor(tone), 0.2), backgroundColor: alpha(threadToneColor(tone), 0.03) }}>
          <Stack spacing={1}>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                <StatusChip label={formatLabel(event.kind)} tone={threadToneChip(tone)} />
                <Typography variant="subtitle2">{source.name}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {source.role}
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {formatDate(event.timestamp)}
              </Typography>
            </Stack>
            <Typography variant="body2">{event.message}</Typography>
            <Typography variant="body2" color="text.secondary">
              {collaboration.headline}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {timelineSummary(event)}
            </Typography>
            <Stack spacing={0.75}>
              {collaboration.notes.map((note) => (
                <Typography key={note} variant="caption" color="text.secondary">
                  {note}
                </Typography>
              ))}
            </Stack>
            {collaboration.tags.length ? (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {collaboration.tags.map((tag) => (
                  <Chip key={tag} label={tag} size="small" variant="outlined" />
                ))}
              </Stack>
            ) : null}
          </Stack>
        </Paper>
      </Box>
    </Box>
  );
}

function OutcomeProjection({ detail }: { detail: CaseDetailView }) {
  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <KeyValueCard
          title="Approval state"
          items={[
            ['Required', detail.approvalState.approvalRequired ? 'Yes' : 'No'],
            ['Status', formatLabel(detail.approvalState.approvalStatus)],
            ['Requested role', detail.approvalState.requestedRole ? formatLabel(detail.approvalState.requestedRole) : 'Not required'],
            ['Requested at', detail.approvalState.requestedAt ? formatDate(detail.approvalState.requestedAt) : 'Not requested'],
            ['Decision by', detail.approvalState.decisionBy ?? 'Pending'],
            ['Decision at', detail.approvalState.decisionAt ? formatDate(detail.approvalState.decisionAt) : 'Pending'],
          ]}
        />
        <KeyValueCard
          title="Final outcome"
          items={detail.outcome ? [
            ['Outcome type', formatLabel(detail.outcome.outcomeType)],
            ['Outcome status', formatLabel(detail.outcome.outcomeStatus)],
            ['Settled at', formatDate(detail.outcome.settledAt)],
            ['Settlement reference', detail.outcome.settlementReference],
          ] : [
            ['Status', 'Outcome not recorded yet'],
            ['Current recommendation', formatLabel(detail.currentRecommendation)],
            ['Next gate', detail.approvalState.approvalStatus === 'PENDING_FINANCE_CONTROL' ? 'Await finance control input' : 'Continue workflow evaluation'],
            ['Workflow status', formatLabel(detail.caseStatus)],
          ]}
        />
      </Stack>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Typography variant="subtitle1" gutterBottom>
          Approval comment and outcome summary
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
          {detail.approvalState.comment || detail.outcome?.summary || 'No approval comment or final outcome summary has been recorded yet.'}
        </Typography>
      </Paper>
    </Stack>
  );
}

function WorkspacePanel({
  detailShell,
  workspaceTab,
  selectedCaseId,
  messageForm,
  approvalForm,
  actionSummary,
  requestTemplates,
  isSubmittingMessage,
  isSubmittingApproval,
  onMessageChange,
  onApprovalChange,
  onSubmitMessage,
  onSubmitApproval,
}: {
  detailShell: DetailShell;
  workspaceTab: WorkspaceTab;
  selectedCaseId: string;
  messageForm: { message: string; operatorId: string; operatorRole: string };
  approvalForm: { decision: string; comment: string; actorId: string; actorRole: string };
  actionSummary: ReturnType<typeof buildActionSummary> | null;
  requestTemplates: RequestTemplate[];
  isSubmittingMessage: boolean;
  isSubmittingApproval: boolean;
  onMessageChange: (patch: Partial<{ message: string; operatorId: string; operatorRole: string }>) => void;
  onApprovalChange: (patch: Partial<{ decision: string; comment: string; actorId: string; actorRole: string }>) => void;
  onSubmitMessage: (event: FormEvent<HTMLFormElement>) => void;
  onSubmitApproval: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const detail = detailShell.kind === 'detail' ? detailShell.detail : null;
  const controlsReady = Boolean(selectedCaseId);

  if (workspaceTab === 'approval') {
    if (!detail || detail.approvalState.approvalStatus !== 'PENDING_FINANCE_CONTROL') {
      return (
        <Alert severity="info">
          Approval controls become active only when the case detail indicates a pending finance-control decision.
        </Alert>
      );
    }

    return (
      <Stack component="form" spacing={2} onSubmit={onSubmitApproval}>
        <Typography variant="subtitle1">Submit approval</Typography>
        <Typography variant="body2" color="text.secondary">
          Requested role: {detail.approvalState.requestedRole ? formatLabel(detail.approvalState.requestedRole) : 'Not specified'}
        </Typography>
        <TextField select label="Decision" value={approvalForm.decision} onChange={(event) => onApprovalChange({ decision: event.target.value })} fullWidth>
          <MenuItem value="APPROVE">Approve</MenuItem>
          <MenuItem value="REJECT">Reject</MenuItem>
        </TextField>
        <TextField label="Comment" value={approvalForm.comment} onChange={(event) => onApprovalChange({ comment: event.target.value })} multiline minRows={4} fullWidth />
        <TextField label="Actor ID" value={approvalForm.actorId} onChange={(event) => onApprovalChange({ actorId: event.target.value })} fullWidth />
        <TextField label="Actor role" value={approvalForm.actorRole} onChange={(event) => onApprovalChange({ actorRole: event.target.value })} fullWidth />
        <Button type="submit" variant="contained" disabled={isSubmittingApproval}>
          {isSubmittingApproval ? 'Submitting...' : 'Submit approval'}
        </Button>
      </Stack>
    );
  }

  return (
    <Stack component="form" spacing={2} onSubmit={onSubmitMessage}>
      <Typography variant="subtitle1">Send request to AI agent</Typography>
      {actionSummary ? (
        <Paper variant="outlined" sx={{ p: 2, backgroundColor: alpha('#245b8f', 0.03) }}>
          <Typography variant="subtitle2" gutterBottom>
            Next operator action
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {actionSummary.title}
          </Typography>
        </Paper>
      ) : null}
      <Typography variant="body2" color="text.secondary">
        {actionSummary?.description ?? 'Send a concise operator instruction after reviewing the current case state.'}
      </Typography>

      <Stack spacing={1.5}>
        <Typography variant="caption" color="text.secondary">
          Templates
        </Typography>
        {requestTemplates.map((template) => (
          <Paper key={template.id} variant="outlined" sx={{ p: 1.5 }}>
            <Stack spacing={1}>
              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
                <Box>
                  <Typography variant="body2">{template.label}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {template.description}
                  </Typography>
                </Box>
                <Button size="small" onClick={() => onMessageChange({ message: template.body })} disabled={!controlsReady || isSubmittingMessage}>
                  Use template
                </Button>
              </Stack>
            </Stack>
          </Paper>
        ))}
      </Stack>

      {actionSummary?.hints?.length ? (
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          {actionSummary.hints.map((hint) => (
            <Chip key={hint} label={hint} size="small" variant="outlined" />
          ))}
        </Stack>
      ) : null}
      {!controlsReady ? (
        <Alert severity="info">
          The detail workspace opened immediately. Message controls unlock after the backend returns the new case identifier.
        </Alert>
      ) : null}
      <TextField
        label="Instruction"
        value={messageForm.message}
        onChange={(event) => onMessageChange({ message: event.target.value })}
        multiline
        minRows={7}
        fullWidth
        disabled={!controlsReady || isSubmittingMessage}
      />
      <TextField label="Operator ID" value={messageForm.operatorId} onChange={(event) => onMessageChange({ operatorId: event.target.value })} fullWidth disabled={!controlsReady || isSubmittingMessage} />
      <TextField label="Operator role" value={messageForm.operatorRole} onChange={(event) => onMessageChange({ operatorRole: event.target.value })} fullWidth disabled={!controlsReady || isSubmittingMessage} />
      <Button type="submit" variant="contained" disabled={!controlsReady || !messageForm.message.trim() || isSubmittingMessage}>
        {isSubmittingMessage ? 'Sending...' : 'Send request'}
      </Button>
    </Stack>
  );
}

function buildActivityThreads(activityHistory: ActivityEvent[]): ActivityThread[] {
  const orderedEvents = [...activityHistory].sort((left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime());
  const threads: ActivityThread[] = [];
  let currentThread: ActivityThread | null = null;

  for (const event of orderedEvents) {
    if (!currentThread || startsNewThread(event, currentThread.events[currentThread.events.length - 1])) {
      if (currentThread) {
        threads.push(currentThread);
      }
      currentThread = {
        id: event.eventId,
        title: buildThreadTitle(event),
        subtitle: formatDate(event.timestamp),
        events: [event],
        tone: eventKindTone(event.kind),
      };
    } else {
      currentThread.events.push(event);
      currentThread.subtitle = `${formatDate(currentThread.events[0].timestamp)} to ${formatDate(event.timestamp)}`;
    }
  }

  if (currentThread) {
    threads.push(currentThread);
  }

  return threads.reverse();
}

function startsNewThread(event: ActivityEvent, previousEvent: ActivityEvent): boolean {
  const normalizedKind = event.kind.toUpperCase();
  const normalizedSource = event.source.toLowerCase();
  const previousTimestamp = new Date(previousEvent.timestamp).getTime();
  const currentTimestamp = new Date(event.timestamp).getTime();

  if (Number.isFinite(previousTimestamp) && Number.isFinite(currentTimestamp) && currentTimestamp - previousTimestamp > 15 * 60 * 1000) {
    return true;
  }

  if (normalizedSource.includes('operator') || normalizedSource.includes('finance')) {
    return true;
  }

  return normalizedKind === 'CASE_CREATED' || normalizedKind === 'APPROVAL_REQUESTED' || normalizedKind === 'APPROVAL_SUBMITTED';
}

function buildThreadTitle(event: ActivityEvent): string {
  const source = summarizeSource(event.source);
  const normalizedKind = event.kind.toUpperCase();

  if (normalizedKind === 'CASE_CREATED') {
    return 'Case opened';
  }
  if (normalizedKind === 'APPROVAL_REQUESTED') {
    return 'Approval requested';
  }
  if (normalizedKind === 'APPROVAL_SUBMITTED') {
    return 'Approval decision';
  }
  if (source.role === 'Human') {
    return `${source.name} request`;
  }

  return `${source.name} workflow thread`;
}

function eventKindTone(kind: string): ActivityThread['tone'] {
  const normalizedKind = kind.toUpperCase();

  if (normalizedKind.includes('APPROVAL')) {
    return 'approval';
  }
  if (normalizedKind.includes('MESSAGE') || normalizedKind.includes('INSTRUCTION') || normalizedKind === 'CASE_CREATED') {
    return 'operator';
  }
  if (normalizedKind.includes('EVIDENCE') || normalizedKind.includes('RECOMMENDATION') || normalizedKind.includes('SETTLEMENT') || normalizedKind.includes('DELEGATION')) {
    return 'workflow';
  }

  return 'system';
}

function threadToneLabel(tone: ActivityThread['tone']): string {
  if (tone === 'operator') {
    return 'Operator turn';
  }
  if (tone === 'approval') {
    return 'Approval';
  }
  if (tone === 'workflow') {
    return 'Workflow';
  }
  return 'System';
}

function threadToneColor(tone: ActivityThread['tone']): string {
  if (tone === 'operator') {
    return '#245b8f';
  }
  if (tone === 'approval') {
    return '#8a4f00';
  }
  if (tone === 'workflow') {
    return '#2e7d32';
  }
  return '#5f6b75';
}

function threadToneChip(tone: ActivityThread['tone']): 'status' | 'recommendation' | 'warning' | 'neutral' | 'success' {
  if (tone === 'operator') {
    return 'status';
  }
  if (tone === 'approval') {
    return 'warning';
  }
  if (tone === 'workflow') {
    return 'success';
  }
  return 'neutral';
}

function buildRequestTemplates(detailShell: DetailShell | null): RequestTemplate[] {
  if (!detailShell) {
    return [];
  }

  if (detailShell.kind === 'create-preview') {
    return [
      {
        id: 'create-initial-check',
        label: 'Start evidence gathering',
        description: 'Ask the workflow to summarize what evidence is still needed first.',
        body: 'Summarize the current case state, identify the highest priority evidence gap, and propose the next single action needed to progress this case.',
      },
      {
        id: 'create-customer-safe-summary',
        label: 'Prepare customer-safe summary',
        description: 'Request a concise summary of the current state for operator follow-up.',
        body: 'Provide a concise operator-facing summary of the case state so far, including what evidence is being collected and what decision is still pending.',
      },
    ];
  }

  const caseTypeLabel = detailShell.kind === 'detail'
    ? formatLabel(detailShell.detail.caseType)
    : formatLabel(detailShell.item.caseType);

  return [
    {
      id: 'next-step',
      label: 'Clarify next step',
      description: 'Ask for one concrete next action instead of a broad recap.',
      body: `For this ${caseTypeLabel} case, identify the single highest priority next step, explain why it matters, and tell me what evidence or action is still missing.`,
    },
    {
      id: 'recommendation-basis',
      label: 'Explain recommendation basis',
      description: 'Request the reasoning behind the current recommendation and evidence gaps.',
      body: `Explain the basis for the current recommendation on this ${caseTypeLabel} case, referencing shipment, escrow, risk, and policy inputs. Call out any uncertainty or missing evidence explicitly.`,
    },
    {
      id: 'operator-follow-up',
      label: 'Draft operator follow-up',
      description: 'Get a concise operator message to move the case forward.',
      body: `Draft a concise operator follow-up for this ${caseTypeLabel} case that advances the workflow without repeating information already present in the case detail projection.`,
    },
  ];
}
