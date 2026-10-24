import AddIcon from '@mui/icons-material/Add'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ReactFlowProvider } from '@xyflow/react'
import { useCallback, useState } from 'react'
import { ApiError, api } from '../api/client'
import { useArtifacts, useInvalidateProject } from '../api/hooks'
import type { Artifact, FlowGraph } from '../api/types'
import ArtifactChips from '../components/ArtifactChips'
import ErrorAlert from '../components/ErrorAlert'
import FlowEditor from '../flow/FlowEditor'
import GenerateFlowDialog from '../flow/GenerateFlowDialog'
import ReviewProposalDialog from '../flow/ReviewProposalDialog'

export default function FlowsSection({ projectId }: { projectId: string }) {
  const flows = useQuery({ queryKey: ['flows', projectId], queryFn: () => api.listFlows(projectId) })
  const artifacts = useArtifacts(projectId)
  const invalidate = useInvalidateProject(projectId)
  const refresh = useCallback(() => {
    invalidate()
    flows.refetch()
  }, [invalidate, flows])
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'generate' | 'new' | null>(null)
  const [notice, setNotice] = useState<string[] | null>(null)

  const list = flows.data ?? []
  const selected = list.find((f) => f.key === selectedKey) ?? list[0]
  const requirements = (artifacts.data ?? []).filter((a) => a.type === 'requirement' && a.status !== 'rejected')

  return (
    <Stack spacing={2} sx={{ height: '100%' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
        <Typography variant="h5" component="h2" sx={{ flexGrow: 1 }}>
          Flows
        </Typography>
        {list.length > 0 && (
          <TextField
            select
            size="small"
            label="Flow"
            value={selected?.key ?? ''}
            onChange={(e) => setSelectedKey(e.target.value)}
            sx={{ minWidth: 220 }}
          >
            {list.map((f) => (
              <MenuItem key={f.key} value={f.key}>
                {f.title}
              </MenuItem>
            ))}
          </TextField>
        )}
        <Button startIcon={<AutoAwesomeIcon />} variant="contained" onClick={() => setDialog('generate')}>
          Generate flow
        </Button>
        <Button startIcon={<AddIcon />} onClick={() => setDialog('new')}>
          Blank flow
        </Button>
      </Stack>

      {flows.isPending && <CircularProgress aria-label="Loading flows" />}
      {flows.isError && <ErrorAlert error={flows.error} />}
      {notice && notice.length > 0 && (
        <Alert severity="warning" onClose={() => setNotice(null)}>
          Saved with warnings:
          <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
            {notice.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </Alert>
      )}
      {flows.isSuccess && list.length === 0 && (
        <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
          No flows yet. Generate one from your confirmed requirements, or draw a blank flow.
        </Box>
      )}

      {selected && (
        <FlowWorkspace
          // Re-mount on every saved version so the canvas starts from the stored graph.
          key={`${selected.key}:${selected.version}`}
          projectId={projectId}
          flow={selected}
          requirements={requirements}
          onSaved={(warnings) => {
            setNotice(warnings)
            refresh()
          }}
        />
      )}

      {dialog === 'generate' && (
        <GenerateFlowDialog
          projectId={projectId}
          requirements={requirements}
          onClose={() => setDialog(null)}
          onCreated={(key, warnings) => {
            setDialog(null)
            setSelectedKey(key)
            setNotice(warnings)
            refresh()
          }}
        />
      )}
      {dialog === 'new' && (
        <NewFlowDialog
          projectId={projectId}
          onClose={() => setDialog(null)}
          onCreated={(key) => {
            setDialog(null)
            setSelectedKey(key)
            refresh()
          }}
        />
      )}
    </Stack>
  )
}

function FlowWorkspace(props: {
  projectId: string
  flow: Artifact
  requirements: Artifact[]
  onSaved: (warnings: string[]) => void
}) {
  const { projectId, flow, requirements, onSaved } = props
  const stored = flow.data.graph ?? { nodes: [], edges: [], schemaVersion: 1 }
  const [current, setCurrent] = useState<FlowGraph>(stored)
  const [dirty, setDirty] = useState(false)
  const [resetCount, setResetCount] = useState(0)
  const [instruction, setInstruction] = useState('')
  const [reviewing, setReviewing] = useState<string | null>(null)

  const pending = useQuery({
    queryKey: ['flow-proposals', projectId, flow.key],
    queryFn: () => api.pendingFlowProposals(projectId, flow.key),
  })
  const save = useMutation({
    mutationFn: () => api.saveFlow(projectId, flow.key, { graph: current, version: flow.version }),
    onSuccess: (result) => onSaved(result.warnings),
  })
  const propose = useMutation({
    mutationFn: () => api.proposeFlowChange(projectId, flow.key, instruction.trim()),
    onSuccess: (result) => {
      setInstruction('')
      setReviewing(result.changeSet.id)
      pending.refetch()
    },
  })
  const onChange = useCallback((graph: FlowGraph, isDirty: boolean) => {
    setCurrent(graph)
    setDirty(isDirty)
  }, [])
  const stale = save.error instanceof ApiError && save.error.code === 'stale-version'

  return (
    <Stack spacing={2} sx={{ flexGrow: 1, minHeight: 0 }}>
      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="h6" component="h3">
          {flow.title}
        </Typography>
        <ArtifactChips artifact={flow} />
        <Box sx={{ flexGrow: 1 }} />
        <Typography variant="body2" color="text.secondary" role="status">
          {save.isPending ? 'Saving…' : dirty ? 'Unsaved changes' : 'All changes saved'}
        </Typography>
        <Button disabled={!dirty || save.isPending} onClick={() => setResetCount((n) => n + 1)}>
          Discard
        </Button>
        <Button variant="contained" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </Stack>
      {stale && <Alert severity="warning">This flow was changed elsewhere. Discard your edits to load the latest.</Alert>}
      {save.isError && !stale && <ErrorAlert error={save.error} />}

      {(pending.data ?? []).map((cs) => (
        <Alert
          key={cs.id}
          severity="info"
          action={
            <Button color="inherit" size="small" onClick={() => setReviewing(cs.id)}>
              Review
            </Button>
          }
        >
          Pending AI proposal: {cs.summary}
        </Alert>
      ))}

      <Box sx={{ height: { xs: 480, md: 'calc(100vh - 360px)' }, minHeight: 420 }}>
        <ReactFlowProvider>
          <FlowEditor key={resetCount} graph={stored} requirements={requirements} onChange={onChange} />
        </ReactFlowProvider>
      </Box>

      <Stack
        component="form"
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
        onSubmit={(e) => {
          e.preventDefault()
          if (instruction.trim()) propose.mutate()
        }}
      >
        <TextField
          fullWidth
          size="small"
          label="Ask AI to change this flow"
          placeholder="e.g. Add a manual approval step before payment"
          value={instruction}
          onChange={(e) => setInstruction(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 2000 } }}
        />
        <Button type="submit" variant="outlined" disabled={!instruction.trim() || propose.isPending} sx={{ flexShrink: 0 }}>
          {propose.isPending ? 'Thinking…' : 'Propose change'}
        </Button>
      </Stack>
      <Typography variant="caption" color="text.secondary">
        The AI proposes specific changes for you to review. Nothing is applied until you approve it.
      </Typography>
      {propose.isError && <ErrorAlert error={propose.error} />}
      {flow.data.graph && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
          <Chip size="small" label={`${current.nodes.length} steps`} />
          <Chip size="small" label={`${current.edges.length} connections`} />
          <Chip
            size="small"
            label={`${new Set(current.nodes.flatMap((n) => n.requirementKeys ?? [])).size} linked requirements`}
          />
        </Stack>
      )}

      {reviewing && (
        <ReviewProposalDialog
          projectId={projectId}
          changeSetId={reviewing}
          current={stored}
          hasUnsavedEdits={dirty}
          onClose={() => setReviewing(null)}
          onDone={() => {
            pending.refetch()
            onSaved([])
          }}
        />
      )}
    </Stack>
  )
}

function NewFlowDialog(props: { projectId: string; onCreated: (key: string) => void; onClose: () => void }) {
  const [title, setTitle] = useState('')
  const create = useMutation({
    mutationFn: () => api.createFlow(props.projectId, title.trim()),
    onSuccess: (flow) => props.onCreated(flow.key),
  })
  return (
    <Dialog open onClose={props.onClose} fullWidth maxWidth="xs">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          create.mutate()
        }}
      >
        <DialogTitle>New blank flow</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
            {create.isError && <ErrorAlert error={create.error} />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={props.onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={!title.trim() || create.isPending}>
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
