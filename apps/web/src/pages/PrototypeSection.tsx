import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import CodeIcon from '@mui/icons-material/Code'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useArtifacts, useInvalidateProject } from '../api/hooks'
import type { Artifact, PrototypeDraft, PrototypeScreen } from '../api/types'
import ArtifactChips from '../components/ArtifactChips'
import ErrorAlert from '../components/ErrorAlert'
import DraftReviewDialog from '../prototype/DraftReviewDialog'
import JsonEditDialog from '../prototype/JsonEditDialog'
import PrototypePlayer from '../prototype/PrototypePlayer'

type AiRequest = { kind: 'generate' } | { kind: 'screen'; screen: PrototypeScreen }

export default function PrototypeSection({ projectId }: { projectId: string }) {
  const prototype = useQuery({ queryKey: ['prototype', projectId], queryFn: () => api.getPrototype(projectId) })
  const artifacts = useArtifacts(projectId)
  const invalidate = useInvalidateProject(projectId)
  const navigate = useNavigate()
  const [tab, setTab] = useState<'preview' | 'screens'>('preview')
  const [startAt, setStartAt] = useState<string | undefined>()
  const [aiRequest, setAiRequest] = useState<AiRequest | null>(null)
  const [draft, setDraft] = useState<PrototypeDraft | null>(null)
  const [editingJson, setEditingJson] = useState(false)
  const [warnings, setWarnings] = useState<string[]>([])

  const current: Artifact | null = prototype.data?.prototype ?? null
  const spec = current?.data.prototype ?? null
  const confirmed = (artifacts.data ?? []).filter((a) => a.type === 'requirement' && a.status === 'confirmed')
  const titleOf = (key: string) => artifacts.data?.find((a) => a.key === key)?.title ?? key

  const onSaved = (w: string[]) => {
    setDraft(null)
    setEditingJson(false)
    setWarnings(w)
    invalidate()
    prototype.refetch()
  }

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
        <Typography variant="h5" component="h2" sx={{ flexGrow: 1 }}>
          Prototype
        </Typography>
        <Button
          variant="contained"
          startIcon={<AutoAwesomeIcon />}
          onClick={() => setAiRequest({ kind: 'generate' })}
          disabled={confirmed.length === 0}
        >
          {spec ? 'Regenerate with AI' : 'Generate with AI'}
        </Button>
        <Button startIcon={<CodeIcon />} onClick={() => setEditingJson(true)}>
          {spec ? 'Edit spec' : 'Write spec by hand'}
        </Button>
      </Stack>

      {confirmed.length === 0 && (
        <Alert severity="info">
          Confirm at least one requirement to generate a prototype. Prototypes are built from confirmed requirements,
          decisions, and flows.
        </Alert>
      )}
      {warnings.length > 0 && (
        <Alert severity="warning" onClose={() => setWarnings([])}>
          Saved with warnings:
          <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
            {warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </Alert>
      )}
      {prototype.isPending && <CircularProgress aria-label="Loading prototype" />}
      {prototype.isError && <ErrorAlert error={prototype.error} />}
      {prototype.isSuccess && !spec && (
        <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
          No prototype yet. Generate one, then click through it here. Interactions are simulated; no real data is
          sent or stored.
        </Box>
      )}

      {current && spec && (
        <>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="h6" component="h3">
              {spec.title}
            </Typography>
            <ArtifactChips artifact={current} />
            <Chip size="small" label={`${spec.screens.length} screens`} />
          </Stack>
          <Tabs value={tab} onChange={(_, v) => setTab(v)}>
            <Tab value="preview" label="Preview" />
            <Tab value="screens" label="Screens" />
          </Tabs>

          {tab === 'preview' && <PrototypePlayer key={`${current.version}:${startAt ?? ''}`} spec={spec} initialScreen={startAt} />}

          {tab === 'screens' && (
            <Stack spacing={1.5}>
              {spec.screens.map((s) => (
                <Card key={s.id} variant="outlined">
                  <CardContent sx={{ pb: 1 }}>
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <Typography variant="subtitle1">{s.title}</Typography>
                        {s.id === spec.startScreen && <Chip size="small" label="start" />}
                        <Chip size="small" variant="outlined" label={s.id} sx={{ fontFamily: 'monospace' }} />
                      </Stack>
                      {s.description && <Typography variant="body2">{s.description}</Typography>}
                      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                        {(s.requirementKeys ?? []).length === 0 && (
                          <Typography variant="caption" color="text.secondary">
                            Not linked to any requirement
                          </Typography>
                        )}
                        {(s.requirementKeys ?? []).map((k) => (
                          <Chip
                            key={k}
                            size="small"
                            label={titleOf(k)}
                            title={k}
                            onClick={() => navigate(`/projects/${projectId}/requirements`)}
                          />
                        ))}
                      </Stack>
                    </Stack>
                  </CardContent>
                  <CardActions>
                    <Button
                      size="small"
                      onClick={() => {
                        setStartAt(s.id)
                        setTab('preview')
                      }}
                    >
                      Preview
                    </Button>
                    <Button size="small" onClick={() => setAiRequest({ kind: 'screen', screen: s })}>
                      Redesign with AI
                    </Button>
                    <Button size="small" onClick={() => navigate(`/projects/${projectId}/flows`)}>
                      Open flows
                    </Button>
                  </CardActions>
                </Card>
              ))}
            </Stack>
          )}
        </>
      )}

      {aiRequest && (
        <AiRequestDialog
          projectId={projectId}
          request={aiRequest}
          onDraft={(d) => {
            setAiRequest(null)
            setDraft(d)
          }}
          onClose={() => setAiRequest(null)}
        />
      )}
      {draft && (
        <DraftReviewDialog
          projectId={projectId}
          draft={draft}
          current={current}
          onSaved={onSaved}
          onClose={() => setDraft(null)}
        />
      )}
      {editingJson && (
        <JsonEditDialog projectId={projectId} current={current} onSaved={onSaved} onClose={() => setEditingJson(false)} />
      )}
    </Stack>
  )
}

function AiRequestDialog(props: {
  projectId: string
  request: AiRequest
  onDraft: (draft: PrototypeDraft) => void
  onClose: () => void
}) {
  const { projectId, request, onDraft, onClose } = props
  const [instruction, setInstruction] = useState('')
  const run = useMutation({
    mutationFn: () =>
      request.kind === 'generate'
        ? api.generatePrototype(projectId, instruction.trim())
        : api.proposeScreen(projectId, request.screen.id, instruction.trim()),
    onSuccess: onDraft,
  })
  const isScreen = request.kind === 'screen'

  return (
    <Dialog open onClose={run.isPending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{isScreen ? `Redesign “${request.screen.title}”` : 'Generate prototype with AI'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label={isScreen ? 'What should change?' : 'Anything to emphasise? (optional)'}
            placeholder={isScreen ? 'e.g. Show the booking status and a cancel button' : 'e.g. Focus on the renter journey'}
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            multiline
            minRows={2}
            autoFocus
          />
          <Typography variant="body2" color="text.secondary">
            You will see a working preview first. Nothing is saved until you choose to save it.
          </Typography>
          {run.isPending && <Alert severity="info">Designing… this can take up to a minute.</Alert>}
          {run.isError && <ErrorAlert error={run.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={run.isPending}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={() => run.mutate()}
          disabled={run.isPending || (isScreen && !instruction.trim())}
        >
          {isScreen ? 'Propose redesign' : 'Generate draft'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
