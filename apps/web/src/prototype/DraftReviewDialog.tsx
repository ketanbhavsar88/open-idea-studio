import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Artifact, PrototypeDraft } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'
import PrototypePlayer from './PrototypePlayer'

interface Props {
  projectId: string
  draft: PrototypeDraft
  current: Artifact | null
  onSaved: (warnings: string[]) => void
  onClose: () => void
}

/** Shows an unsaved AI draft as a working prototype; nothing changes until the user saves it. */
export default function DraftReviewDialog({ projectId, draft, current, onSaved, onClose }: Props) {
  const save = useMutation({
    mutationFn: () => api.savePrototype(projectId, { spec: draft.spec, version: current?.version ?? null, fromAi: true }),
    onSuccess: (result) => onSaved(result.warnings),
  })
  const screen = draft.screenId ? draft.spec.screens.find((s) => s.id === draft.screenId) : null

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>{screen ? `Review redesigned screen: ${screen.title}` : 'Review AI prototype draft'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {screen
              ? 'Only this screen changes. Click through to check it, then save or discard.'
              : current
                ? 'Saving replaces the current prototype. The previous version stays in version history.'
                : 'Click through the draft, then save it or discard it.'}
          </Typography>
          {draft.warnings.map((w) => (
            <Alert key={w} severity="warning">
              {w}
            </Alert>
          ))}
          <PrototypePlayer spec={draft.spec} initialScreen={draft.screenId ?? undefined} />
          {save.isError && <ErrorAlert error={save.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={save.isPending}>
          Discard
        </Button>
        <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save prototype'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
