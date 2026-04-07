import type { FormEvent } from 'react';
import { Alert, AppBar, Box, Button, CssBaseline, Snackbar, ThemeProvider, Toolbar, Typography } from '@mui/material';
import { useOperatorConsole } from './operator-console/hooks/useOperatorConsole';
import { DetailScreen } from './operator-console/components/DetailScreen';
import { ListScreen } from './operator-console/components/ListScreen';
import { CreateCaseDialog } from './operator-console/components/CreateCaseDialog';
import { operatorConsoleTheme } from './operator-console/theme';
import type { DetailShell } from './operator-console/components/view-model';

export default function App() {
  const { state, actions } = useOperatorConsole();

  const selectedListPreview = state.selectedCaseId && state.selectedListCase?.caseId === state.selectedCaseId
    ? state.selectedListCase
    : null;

  const detailShell: DetailShell | null = state.selectedCaseDetail
    ? { kind: 'detail', detail: state.selectedCaseDetail }
    : state.pendingCreatePreview
      ? { kind: 'create-preview', preview: state.pendingCreatePreview }
      : selectedListPreview
        ? { kind: 'list-preview', item: selectedListPreview }
        : null;

  function handleCreateCase(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void actions.createCase();
  }

  function handleSubmitMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void actions.submitMessage();
  }

  function handleSubmitApproval(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void actions.submitApproval();
  }

  return (
    <ThemeProvider theme={operatorConsoleTheme}>
      <CssBaseline />

      <Box sx={{ minHeight: '100vh', background: 'linear-gradient(180deg, #eef2f5 0%, #f7f8fa 280px, #f3f5f7 100%)' }}>
        <AppBar position="sticky" color="transparent" elevation={0} sx={{ backdropFilter: 'blur(16px)', borderBottom: '1px solid', borderColor: 'divider' }}>
          <Toolbar sx={{ gap: 2 }}>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="overline" color="text.secondary">
                Marketplace Agent Platform
              </Typography>
              <Typography variant="h6">Operator Console</Typography>
            </Box>

            <Button variant={state.viewMode === 'list' ? 'contained' : 'text'} onClick={() => actions.setViewMode('list')}>
              Case list
            </Button>
            <Button
              variant={state.viewMode === 'detail' ? 'contained' : 'text'}
              onClick={() => {
                if (detailShell) {
                  actions.setViewMode('detail');
                }
              }}
              disabled={!detailShell}
            >
              Case detail
            </Button>
            <Button variant="outlined" onClick={actions.refreshCases}>
              Refresh
            </Button>
          </Toolbar>
        </AppBar>

        <Box sx={{ px: { xs: 2, md: 4 }, py: 3 }}>
          {state.errorMessage ? (
            <Alert severity="error" sx={{ mb: 2 }}>
              {state.errorMessage}
            </Alert>
          ) : null}

          {state.viewMode === 'list' ? (
            <ListScreen
              cases={state.cases}
              selectedCaseId={state.selectedCaseId}
              filters={state.filters}
              isLoading={state.isLoadingCases}
              onFiltersChange={actions.refreshWithFilters}
              onOpenCase={actions.openCase}
              onCreateCase={() => actions.setIsCreateOpen(true)}
              onRefresh={actions.refreshCases}
            />
          ) : (
            <DetailScreen
              detailShell={detailShell}
              selectedCaseId={state.selectedCaseId}
              isLoadingDetail={state.isLoadingDetail || state.isSubmittingCreate}
              workspaceTab={state.workspaceTab}
              detailTab={state.detailTab}
              messageForm={state.messageForm}
              approvalForm={state.approvalForm}
              isSubmittingCreate={state.isSubmittingCreate}
              isSubmittingMessage={state.isSubmittingMessage}
              isSubmittingApproval={state.isSubmittingApproval}
              onChangeDetailTab={actions.setDetailTab}
              onChangeWorkspaceTab={actions.setWorkspaceTab}
              onRefresh={actions.refreshCases}
              onMessageChange={(patch) => actions.setMessageForm((current) => ({ ...current, ...patch }))}
              onApprovalChange={(patch) => actions.setApprovalForm((current) => ({ ...current, ...patch }))}
              onSubmitMessage={handleSubmitMessage}
              onSubmitApproval={handleSubmitApproval}
            />
          )}
        </Box>
      </Box>

      <CreateCaseDialog
        open={state.isCreateOpen}
        form={state.createForm}
        submitting={state.isSubmittingCreate}
        onClose={() => actions.setIsCreateOpen(false)}
        onChange={(patch) => actions.setCreateForm((current) => ({ ...current, ...patch }))}
        onSubmit={handleCreateCase}
      />

      <Snackbar
        open={Boolean(state.flashMessage)}
        autoHideDuration={4000}
        onClose={actions.clearFlashMessage}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity="success" onClose={actions.clearFlashMessage} variant="filled" sx={{ width: '100%' }}>
          {state.flashMessage}
        </Alert>
      </Snackbar>
    </ThemeProvider>
  );
}
