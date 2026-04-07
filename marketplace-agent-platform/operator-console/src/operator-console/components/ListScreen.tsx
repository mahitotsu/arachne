import { Box, Button, Chip, Divider, List, ListItemButton, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { buildCaseHeadline, formatCurrency, formatLabel, formatRelativeTime } from '../lib/formatters';
import type { CaseListItem } from '../../types';
import { EmptyState, StatusChip, SummaryMetric } from './common';
import { CASE_STATUS_OPTIONS, CASE_TYPE_OPTIONS } from './view-model';

export interface ListScreenProps {
  cases: CaseListItem[];
  selectedCaseId: string;
  filters: { q: string; caseStatus: string; caseType: string };
  isLoading: boolean;
  onFiltersChange: (patch: Partial<{ q: string; caseStatus: string; caseType: string }>) => void;
  onOpenCase: (caseId: string) => void;
  onCreateCase: () => void;
  onRefresh: () => void;
}

export function ListScreen({
  cases,
  selectedCaseId,
  filters,
  isLoading,
  onFiltersChange,
  onOpenCase,
  onCreateCase,
  onRefresh,
}: ListScreenProps) {
  const orderedCases = [...cases].sort((left, right) => {
    const leftRank = left.pendingApproval ? 0 : left.caseStatus === 'COMPLETED' ? 2 : 1;
    const rightRank = right.pendingApproval ? 0 : right.caseStatus === 'COMPLETED' ? 2 : 1;
    if (leftRank !== rightRank) {
      return leftRank - rightRank;
    }

    return right.updatedAt.localeCompare(left.updatedAt);
  });

  const openCount = cases.filter((item) => item.caseStatus !== 'COMPLETED').length;
  const approvalCount = cases.filter((item) => item.pendingApproval).length;
  const completedCount = cases.filter((item) => item.caseStatus === 'COMPLETED').length;

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 340px' }, gap: 3 }}>
      <Box sx={{ display: 'grid', gap: 3 }}>
        <Paper sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Box>
              <Typography variant="overline" color="text.secondary">
                Queue Overview
              </Typography>
              <Typography variant="h4">Case list</Typography>
              <Typography color="text.secondary">
                Centralize triage in one list. Open detail immediately, then let the backend fill in evidence and history asynchronously.
              </Typography>
            </Box>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <SummaryMetric label="Visible" value={String(cases.length)} tone="neutral" />
              <SummaryMetric label="Active" value={String(openCount)} tone="primary" />
              <SummaryMetric label="Awaiting approval" value={String(approvalCount)} tone="warning" />
              <SummaryMetric label="Completed" value={String(completedCount)} tone="success" />
            </Stack>
          </Stack>
        </Paper>

        <Paper sx={{ overflow: 'hidden' }}>
          <Box sx={{ px: 3, py: 2.5, borderBottom: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6">Case queue</Typography>
            <Typography color="text.secondary">
              Priority cases are surfaced first. Selecting a case opens the detail workspace without waiting for full backend hydration.
            </Typography>
          </Box>

          {orderedCases.length ? (
            <List disablePadding aria-busy={isLoading}>
              {orderedCases.map((item, index) => (
                <Box key={item.caseId}>
                  {index > 0 ? <Divider /> : null}
                  <ListItemButton selected={item.caseId === selectedCaseId} onClick={() => onOpenCase(item.caseId)} sx={{ px: 3, py: 2.5, alignItems: 'flex-start' }}>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1.2fr) auto auto' }, gap: 2, width: '100%' }}>
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                          <Typography variant="subtitle1">{buildCaseHeadline(item)}</Typography>
                          <Chip label={item.caseId} size="small" variant="outlined" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                          Order {item.orderId} · Updated {formatRelativeTime(item.updatedAt)}
                        </Typography>
                      </Box>

                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        <StatusChip label={formatLabel(item.caseStatus)} tone="status" />
                        <StatusChip label={formatLabel(item.currentRecommendation)} tone="recommendation" />
                        <StatusChip label={formatLabel(item.approvalStatus)} tone={item.pendingApproval ? 'warning' : 'neutral'} />
                      </Stack>

                      <Box sx={{ textAlign: { xs: 'left', md: 'right' } }}>
                        <Typography variant="subtitle2">{formatCurrency(item.amount, item.currency)}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.pendingApproval ? 'Approval pending' : formatLabel(item.caseType)}
                        </Typography>
                      </Box>
                    </Box>
                  </ListItemButton>
                </Box>
              ))}
            </List>
          ) : (
            <EmptyState
              title="No cases found"
              body="Adjust the filters or create a new case from the right-side control panel."
            />
          )}
        </Paper>
      </Box>

      <Stack spacing={3} alignSelf="start">
        <Paper sx={{ p: 3 }}>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="overline" color="text.secondary">
                Actions
              </Typography>
              <Typography variant="h6">Search and create</Typography>
            </Box>

            <Button variant="contained" size="large" onClick={onCreateCase}>
              New case
            </Button>
            <Button variant="outlined" onClick={onRefresh}>
              Refresh queue
            </Button>

            <TextField
              label="Search"
              value={filters.q}
              onChange={(event) => onFiltersChange({ q: event.target.value })}
              placeholder="Case ID or order ID"
              fullWidth
            />

            <TextField
              select
              label="Status"
              value={filters.caseStatus}
              onChange={(event) => onFiltersChange({ caseStatus: event.target.value })}
              fullWidth
            >
              <MenuItem value="">All statuses</MenuItem>
              {CASE_STATUS_OPTIONS.map((status) => (
                <MenuItem key={status} value={status}>
                  {formatLabel(status)}
                </MenuItem>
              ))}
            </TextField>

            <TextField
              select
              label="Case type"
              value={filters.caseType}
              onChange={(event) => onFiltersChange({ caseType: event.target.value })}
              fullWidth
            >
              <MenuItem value="">All case types</MenuItem>
              {CASE_TYPE_OPTIONS.map((caseType) => (
                <MenuItem key={caseType} value={caseType}>
                  {formatLabel(caseType)}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </Paper>

        <Paper sx={{ p: 3, backgroundColor: alpha('#245b8f', 0.04) }}>
          <Stack spacing={1.5}>
            <Typography variant="subtitle2" color="text.secondary">
              Working guidance
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Keep the list dense and operational. Use the detail screen only after choosing a single case, and let deeper evidence arrive in place instead of blocking navigation.
            </Typography>
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
}
