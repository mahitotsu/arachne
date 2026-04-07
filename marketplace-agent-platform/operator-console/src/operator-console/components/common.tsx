import { Box, Chip, Paper, Skeleton, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { formatCurrency, formatLabel, formatRelativeTime } from '../lib/formatters';
import type { DetailShell } from './view-model';

export function SummaryMetric({ label, value, tone }: { label: string; value: string; tone: 'neutral' | 'primary' | 'warning' | 'success' }) {
  const toneColor = tone === 'primary'
    ? '#245b8f'
    : tone === 'warning'
      ? '#b26a00'
      : tone === 'success'
        ? '#2e7d32'
        : '#546e7a';

  return (
    <Paper variant="outlined" sx={{ p: 2, minWidth: 0, flex: 1, borderColor: alpha(toneColor, 0.2), backgroundColor: alpha(toneColor, 0.03) }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h6" sx={{ mt: 0.5 }}>
        {value}
      </Typography>
    </Paper>
  );
}

export function StatusChip({ label, tone }: { label: string; tone: 'status' | 'recommendation' | 'warning' | 'neutral' | 'success' }) {
  const style = tone === 'status'
    ? { color: '#245b8f', backgroundColor: alpha('#245b8f', 0.1) }
    : tone === 'recommendation'
      ? { color: '#5f3d00', backgroundColor: alpha('#d5a021', 0.18) }
      : tone === 'warning'
        ? { color: '#8a4f00', backgroundColor: alpha('#ef9f27', 0.18) }
        : tone === 'success'
          ? { color: '#1f6a23', backgroundColor: alpha('#2e7d32', 0.12) }
          : { color: '#455a64', backgroundColor: alpha('#90a4ae', 0.18) };

  return <Chip label={label} size="small" sx={style} />;
}

export function KeyValueCard({ title, items }: { title: string; items: Array<[string, string]> }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5, flex: 1 }}>
      <Typography variant="subtitle1" gutterBottom>
        {title}
      </Typography>
      <Stack spacing={1.5}>
        {items.map(([label, value]) => (
          <Box key={label}>
            <Typography variant="caption" color="text.secondary">
              {label}
            </Typography>
            <Typography variant="body2">{value}</Typography>
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}

export function PreviewNarrative({ detailShell }: { detailShell: DetailShell }) {
  if (detailShell.kind === 'list-preview') {
    return (
      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Typography variant="subtitle1" gutterBottom>
          Initial queue snapshot
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Case {detailShell.item.caseId} is currently {formatLabel(detailShell.item.caseStatus)} with recommendation {formatLabel(detailShell.item.currentRecommendation)}.
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Financial exposure is {formatCurrency(detailShell.item.amount, detailShell.item.currency)} and the case was last updated {formatRelativeTime(detailShell.item.updatedAt)}.
        </Typography>
      </Paper>
    );
  }

  if (detailShell.kind === 'create-preview') {
    return (
      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Typography variant="subtitle1" gutterBottom>
          Pending creation snapshot
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {formatLabel(detailShell.preview.caseType)} for order {detailShell.preview.orderId || 'pending order id'} with amount {detailShell.preview.amount || '0'} {detailShell.preview.currency}.
        </Typography>
      </Paper>
    );
  }

  return (
    <EmptyState title="Detailed case loaded" body="The overview is already populated from the full case detail payload." />
  );
}

export function EvidenceBlock({ title, value }: { title: string; value: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="subtitle1" gutterBottom>
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
        {value || 'No evidence returned.'}
      </Typography>
    </Paper>
  );
}

export function LoadingPlaceholder({ body, active }: { body: string; active: boolean }) {
  return (
    <Stack spacing={2}>
      {active ? <Skeleton variant="rounded" height={88} /> : null}
      <Typography variant="body2" color="text.secondary">
        {body}
      </Typography>
    </Stack>
  );
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <Box sx={{ p: 4 }}>
      <Typography variant="h6" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary">{body}</Typography>
    </Box>
  );
}
