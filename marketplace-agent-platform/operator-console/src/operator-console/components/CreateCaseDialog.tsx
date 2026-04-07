import { useMemo, type FormEvent } from 'react';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { formatLabel } from '../lib/formatters';
import type { CaseType } from '../../types';
import { CASE_TYPE_OPTIONS, demoCasePresets } from './view-model';

export interface CreateCaseDialogProps {
  open: boolean;
  form: {
    caseType: CaseType;
    orderId: string;
    amount: string;
    currency: string;
    initialMessage: string;
    operatorId: string;
    operatorRole: string;
  };
  submitting: boolean;
  onClose: () => void;
  onChange: (patch: Partial<{
    caseType: CaseType;
    orderId: string;
    amount: string;
    currency: string;
    initialMessage: string;
    operatorId: string;
    operatorRole: string;
  }>) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function CreateCaseDialog({
  open,
  form,
  submitting,
  onClose,
  onChange,
  onSubmit,
}: CreateCaseDialogProps) {
  const selectedPresetId = useMemo(() => {
    const matchedPreset = demoCasePresets.find((preset) => (
      preset.values.caseType === form.caseType
      && preset.values.orderId === form.orderId
      && preset.values.amount === form.amount
      && preset.values.currency === form.currency
      && preset.values.initialMessage === form.initialMessage
      && preset.values.operatorId === form.operatorId
      && preset.values.operatorRole === form.operatorRole
    ));

    return matchedPreset?.id ?? '';
  }, [form]);

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={onSubmit}>
        <DialogTitle>New case</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              select
              label="Demo preset"
              value={selectedPresetId}
              onChange={(event) => {
                const nextPreset = demoCasePresets.find((preset) => preset.id === event.target.value);
                if (!nextPreset) {
                  return;
                }

                onChange(nextPreset.values);
              }}
              fullWidth
              helperText="Optional. Select a pre-verified demo input to create a case against prepared scenario data."
            >
              <MenuItem value="">Manual entry</MenuItem>
              {demoCasePresets.map((preset) => (
                <MenuItem key={preset.id} value={preset.id}>
                  {preset.label}
                </MenuItem>
              ))}
            </TextField>
            {selectedPresetId ? (
              <Typography variant="caption" color="text.secondary">
                {demoCasePresets.find((preset) => preset.id === selectedPresetId)?.description}
              </Typography>
            ) : null}
            <TextField
              select
              label="Case type"
              value={form.caseType}
              onChange={(event) => onChange({ caseType: event.target.value as CaseType })}
              fullWidth
            >
              {CASE_TYPE_OPTIONS.map((caseType) => (
                <MenuItem key={caseType} value={caseType}>
                  {formatLabel(caseType)}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="Order ID" value={form.orderId} onChange={(event) => onChange({ orderId: event.target.value })} fullWidth />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Amount" type="number" value={form.amount} onChange={(event) => onChange({ amount: event.target.value })} fullWidth />
              <TextField label="Currency" value={form.currency} onChange={(event) => onChange({ currency: event.target.value.toUpperCase() })} fullWidth />
            </Stack>
            <TextField
              label="Initial message"
              value={form.initialMessage}
              onChange={(event) => onChange({ initialMessage: event.target.value })}
              multiline
              minRows={5}
              fullWidth
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Operator ID" value={form.operatorId} onChange={(event) => onChange({ operatorId: event.target.value })} fullWidth />
              <TextField label="Operator role" value={form.operatorRole} onChange={(event) => onChange({ operatorRole: event.target.value })} fullWidth />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={submitting || !form.orderId.trim() || !form.amount.trim()}>
            {submitting ? 'Creating...' : 'Create case'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
