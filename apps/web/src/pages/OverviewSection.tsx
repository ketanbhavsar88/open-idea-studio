import { Alert, Button, Card, CardContent, Chip, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, api } from '../api/client'
import { useArtifacts } from '../api/hooks'
import type { Project } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'

export default function OverviewSection({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient()
  const project = queryClient.getQueryData<Project>(['project', projectId])!
  // Re-mount the form when the saved version changes so drafts start from fresh data.
  return <OverviewForm key={project.version} project={project} />
}

function OverviewForm({ project }: { project: Project }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description)
  const dirty = name !== project.name || description !== project.description

  const save = useMutation({
    mutationFn: () => api.updateProject(project.id, { name, description, version: project.version }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['project', project.id], saved)
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      queryClient.invalidateQueries({ queryKey: ['revisions', project.id] })
    },
  })
  const stale = save.error instanceof ApiError && save.error.code === 'stale-version'

  return (
    <Stack spacing={2} sx={{ maxWidth: 760 }}>
      <Typography variant="h5" component="h2">
        Overview
      </Typography>
      <TextField label="Project name" value={name} onChange={(e) => setName(e.target.value)} required />
      <TextField
        label="Idea"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        multiline
        minRows={5}
      />
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <Button variant="contained" disabled={!dirty || !name.trim() || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
        <Typography variant="body2" color="text.secondary" role="status">
          {dirty ? 'Unsaved changes' : 'All changes saved'}
        </Typography>
      </Stack>
      {stale && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" onClick={() => queryClient.invalidateQueries({ queryKey: ['project', project.id] })}>
              Reload
            </Button>
          }
        >
          This project was changed elsewhere. Reload to get the latest version (your unsaved text will be discarded).
        </Alert>
      )}
      {save.isError && !stale && <ErrorAlert error={save.error} />}
      <IdeaSummary projectId={project.id} />
    </Stack>
  )
}

function IdeaSummary({ projectId }: { projectId: string }) {
  const artifacts = useArtifacts(projectId)
  const all = artifacts.data ?? []
  const summary = all.find((a) => a.type === 'summary')
  const count = (type: string, status: string) => all.filter((a) => a.type === type && a.status === status).length
  const openQuestions = all.filter((a) => a.type === 'question' && a.status === 'open')

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
        <Chip label={`${count('requirement', 'confirmed')} confirmed requirements`} />
        <Chip label={`${count('requirement', 'proposed')} proposed requirements`} />
        <Chip label={`${count('decision', 'confirmed')} confirmed decisions`} />
        <Chip label={`${openQuestions.length} open questions`} />
      </Stack>
      {summary ? (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Typography variant="h6" component="h3">
                  Idea summary
                </Typography>
                <Chip size="small" variant="outlined" label={summary.status === 'edited' ? 'edited by you' : 'AI generated'} />
              </Stack>
              <Typography variant="body2">{summary.data.description}</Typography>
              {summary.data.problem && (
                <Typography variant="body2">
                  <strong>Problem:</strong> {summary.data.problem}
                </Typography>
              )}
              {summary.data.targetUsers && summary.data.targetUsers.length > 0 && (
                <Typography variant="body2">
                  <strong>Target users:</strong> {summary.data.targetUsers.join(', ')}
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Alert severity="info" variant="outlined">
          No idea summary yet. Start discovery in the AI assistant to get one.
        </Alert>
      )}
      {openQuestions.length > 0 && (
        <Alert severity="info">
          <strong>Next recommended step:</strong> answer “{openQuestions[0].title}” in the discussion, or skip it in
          Decisions and questions.
        </Alert>
      )}
    </Stack>
  )
}
