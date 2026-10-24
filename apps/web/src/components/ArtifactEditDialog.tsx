import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { useInvalidateProject } from '../api/hooks'
import { ARTIFACT_STATUSES, REQUIREMENT_CATEGORIES } from '../api/types'
import type { Artifact, ArtifactData, ArtifactType } from '../api/types'
import ErrorAlert from './ErrorAlert'

interface Props {
  projectId: string
  /** Edit this artifact, or create a new one of {@link type} when absent. */
  artifact?: Artifact
  type: ArtifactType
  onClose: () => void
}

export default function ArtifactEditDialog({ projectId, artifact, type, onClose }: Props) {
  const invalidate = useInvalidateProject(projectId)
  const d = artifact?.data ?? {}
  const [title, setTitle] = useState(artifact?.title ?? '')
  const [status, setStatus] = useState(artifact?.status ?? ARTIFACT_STATUSES[type][0])
  const [description, setDescription] = useState(d.description ?? '')
  const [category, setCategory] = useState(d.category ?? '')
  const [priority, setPriority] = useState(d.priority ?? '')
  const [criteria, setCriteria] = useState((d.acceptanceCriteria ?? []).join('\n'))
  const [answer, setAnswer] = useState(d.answer ?? '')
  const [impact, setImpact] = useState(d.impact ?? '')

  const save = useMutation({
    mutationFn: () => {
      const data: ArtifactData = { ...d, description: description || null }
      if (type === 'requirement') {
        data.category = category || null
        data.priority = (priority || null) as ArtifactData['priority']
        const lines = criteria.split('\n').map((l) => l.trim()).filter(Boolean)
        data.acceptanceCriteria = lines.length ? lines : null
      }
      if (type === 'question') data.answer = answer || null
      if (type === 'risk') data.impact = (impact || null) as ArtifactData['impact']
      return artifact
        ? api.updateArtifact(projectId, artifact.key, { title, status, data, version: artifact.version })
        : api.createArtifact(projectId, { type, title, status, data })
    },
    onSuccess: () => {
      invalidate()
      onClose()
    },
  })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <DialogTitle>{artifact ? `Edit ${artifact.key}` : `New ${type}`}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
            <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
              {ARTIFACT_STATUSES[type].map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              minRows={3}
            />
            {type === 'requirement' && (
              <>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField select fullWidth label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
                    <MenuItem value="">(none)</MenuItem>
                    {REQUIREMENT_CATEGORIES.map((c) => (
                      <MenuItem key={c} value={c}>
                        {c}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField select fullWidth label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
                    <MenuItem value="">(none)</MenuItem>
                    {['must', 'should', 'could'].map((p) => (
                      <MenuItem key={p} value={p}>
                        {p}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
                <TextField
                  label="Acceptance criteria"
                  helperText="One per line"
                  value={criteria}
                  onChange={(e) => setCriteria(e.target.value)}
                  multiline
                  minRows={3}
                />
              </>
            )}
            {type === 'question' && (
              <TextField label="Answer" value={answer} onChange={(e) => setAnswer(e.target.value)} multiline minRows={2} />
            )}
            {type === 'risk' && (
              <TextField select label="Impact" value={impact} onChange={(e) => setImpact(e.target.value)}>
                <MenuItem value="">(none)</MenuItem>
                {['low', 'medium', 'high'].map((i) => (
                  <MenuItem key={i} value={i}>
                    {i}
                  </MenuItem>
                ))}
              </TextField>
            )}
            {save.isError && <ErrorAlert error={save.error} />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={!title.trim() || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
