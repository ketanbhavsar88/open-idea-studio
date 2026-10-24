import { Button, Card, CardActions, CardContent, Stack, Typography } from '@mui/material'
import { useUpdateArtifact } from '../api/hooks'
import type { Artifact } from '../api/types'
import ArtifactChips from './ArtifactChips'
import ErrorAlert from './ErrorAlert'

export interface StatusAction {
  label: string
  status: string
}

interface Props {
  projectId: string
  artifact: Artifact
  actions: StatusAction[]
  onEdit: () => void
}

export default function ArtifactCard({ projectId, artifact, actions, onEdit }: Props) {
  const update = useUpdateArtifact(projectId)
  const d = artifact.data
  return (
    <Card variant="outlined">
      <CardContent sx={{ pb: 1 }}>
        <Stack spacing={1}>
          <Typography variant="subtitle1" component="h3">
            {artifact.title}
          </Typography>
          <ArtifactChips artifact={artifact} />
          {d.description && <Typography variant="body2">{d.description}</Typography>}
          {d.acceptanceCriteria && d.acceptanceCriteria.length > 0 && (
            <Typography component="ul" variant="body2" sx={{ m: 0, pl: 3 }}>
              {d.acceptanceCriteria.map((c) => (
                <li key={c}>{c}</li>
              ))}
            </Typography>
          )}
          {d.why && (
            <Typography variant="body2" color="text.secondary">
              Why it matters: {d.why}
            </Typography>
          )}
          {d.answer && (
            <Typography variant="body2">
              <strong>Answer:</strong> {d.answer}
            </Typography>
          )}
          {d.rationale && (
            <Typography variant="body2" color="text.secondary">
              Rationale: {d.rationale}
            </Typography>
          )}
          {update.isError && <ErrorAlert error={update.error} />}
        </Stack>
      </CardContent>
      <CardActions sx={{ flexWrap: 'wrap' }}>
        {actions
          .filter((a) => a.status !== artifact.status)
          .map((a) => (
            <Button
              key={a.status}
              size="small"
              disabled={update.isPending}
              onClick={() => update.mutate({ artifact, changes: { status: a.status } })}
            >
              {a.label}
            </Button>
          ))}
        <Button size="small" onClick={onEdit}>
          Edit
        </Button>
      </CardActions>
    </Card>
  )
}
