import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ReactFlowProvider } from '@xyflow/react'
import { useMemo } from 'react'
import { ApiError, api } from '../api/client'
import type { FlowGraph } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'
import { describeOperation } from './convert'
import { FlowPreview } from './FlowEditor'

interface Props {
  projectId: string
  changeSetId: string
  current: FlowGraph
  /** True when the canvas has unsaved edits; applying would conflict with them. */
  hasUnsavedEdits: boolean
  onDone: () => void
  onClose: () => void
}

export default function ReviewProposalDialog(props: Props) {
  const { projectId, changeSetId, current, hasUnsavedEdits, onDone, onClose } = props
  const proposal = useQuery({
    queryKey: ['changeset', projectId, changeSetId],
    queryFn: () => api.getChangeSet(projectId, changeSetId),
  })
  const apply = useMutation({ mutationFn: () => api.applyChangeSet(projectId, changeSetId), onSettled: onDone })
  const reject = useMutation({ mutationFn: () => api.rejectChangeSet(projectId, changeSetId), onSuccess: onDone })

  const cs = proposal.data?.changeSet
  const preview = proposal.data?.preview
  const highlight = useMemo(() => {
    const ids = new Set<string>()
    cs?.operations.forEach((op) => {
      if (op.op === 'addNode' && op.nodeId) ids.add(op.nodeId)
      if (op.op === 'updateNode' && op.nodeId) ids.add(op.nodeId)
      if (op.op === 'addEdge' && op.edgeId) ids.add(op.edgeId)
    })
    return ids
  }, [cs])
  const stale = apply.error instanceof ApiError && apply.error.status === 409

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Review AI proposal</DialogTitle>
      <DialogContent>
        {proposal.isPending && <Typography>Loading…</Typography>}
        {proposal.isError && <ErrorAlert error={proposal.error} />}
        {cs && preview && (
          <Stack spacing={2}>
            {cs.instruction && (
              <Typography variant="body2" color="text.secondary">
                You asked: “{cs.instruction}”
              </Typography>
            )}
            <Typography variant="subtitle1">{cs.summary}</Typography>
            {cs.warnings.map((w) => (
              <Alert key={w} severity="warning">
                {w}
              </Alert>
            ))}
            <List dense disablePadding>
              {cs.operations.map((op, i) => (
                <ListItem key={i} disableGutters>
                  <ListItemText primary={`${i + 1}. ${describeOperation(op, current, preview)}`} />
                </ListItem>
              ))}
            </List>
            <Typography variant="subtitle2">Preview (changed items highlighted)</Typography>
            <ReactFlowProvider>
              <FlowPreview graph={preview} highlight={highlight} />
            </ReactFlowProvider>
            {cs.state !== 'pending' && <Alert severity="info">This proposal is {cs.state}.</Alert>}
            {hasUnsavedEdits && cs.state === 'pending' && (
              <Alert severity="warning">Save or discard your canvas edits before applying this proposal.</Alert>
            )}
            {stale && (
              <Alert severity="warning">
                The flow changed after this proposal was made, so it was not applied. Ask the AI again.
              </Alert>
            )}
            {apply.isError && !stale && <ErrorAlert error={apply.error} />}
            {reject.isError && <ErrorAlert error={reject.error} />}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {cs?.state === 'pending' && (
          <>
            <Button color="error" onClick={() => reject.mutate()} disabled={reject.isPending || apply.isPending}>
              Reject
            </Button>
            <Button
              variant="contained"
              onClick={() => apply.mutate()}
              disabled={hasUnsavedEdits || apply.isPending || reject.isPending || cs.operations.length === 0}
            >
              Apply changes
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  )
}
