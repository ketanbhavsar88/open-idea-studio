import AddIcon from '@mui/icons-material/Add'
import { Button, CircularProgress, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { useArtifacts } from '../api/hooks'
import type { Artifact, ArtifactType } from '../api/types'
import ArtifactCard from '../components/ArtifactCard'
import type { StatusAction } from '../components/ArtifactCard'
import ArtifactEditDialog from '../components/ArtifactEditDialog'
import ErrorAlert from '../components/ErrorAlert'

interface Group {
  title: string
  description: string
  type: ArtifactType
  filter: (a: Artifact) => boolean
  actions: StatusAction[]
}

const GROUPS: Group[] = [
  {
    title: 'Open questions',
    description: 'Decisions still needed. Answer here or in the discussion.',
    type: 'question',
    filter: (a) => a.type === 'question' && a.status === 'open',
    actions: [{ label: 'Skip for now', status: 'skipped' }],
  },
  {
    title: 'Decisions',
    description: 'Only decisions you confirm count as decided.',
    type: 'decision',
    filter: (a) => a.type === 'decision',
    actions: [
      { label: 'Confirm', status: 'confirmed' },
      { label: 'Reverse', status: 'reversed' },
      { label: 'Reject', status: 'rejected' },
    ],
  },
  {
    title: 'Assumptions',
    description: 'Plausible but unconfirmed statements.',
    type: 'assumption',
    filter: (a) => a.type === 'assumption',
    actions: [
      { label: 'Confirm', status: 'confirmed' },
      { label: 'Reject', status: 'rejected' },
    ],
  },
  {
    title: 'Risks',
    description: 'Potential issues and their impact.',
    type: 'risk',
    filter: (a) => a.type === 'risk',
    actions: [
      { label: 'Mitigated', status: 'mitigated' },
      { label: 'Accept', status: 'accepted' },
      { label: 'Dismiss', status: 'dismissed' },
    ],
  },
  {
    title: 'Answered and skipped questions',
    description: 'Skipped questions can be reopened at any time.',
    type: 'question',
    filter: (a) => a.type === 'question' && a.status !== 'open',
    actions: [{ label: 'Reopen', status: 'open' }],
  },
]

export default function DecisionsSection({ projectId }: { projectId: string }) {
  const artifacts = useArtifacts(projectId)
  const [editing, setEditing] = useState<{ type: ArtifactType; artifact?: Artifact } | null>(null)

  return (
    <Stack spacing={4} sx={{ maxWidth: 900 }}>
      <Typography variant="h5" component="h2">
        Decisions and questions
      </Typography>
      {artifacts.isPending && <CircularProgress aria-label="Loading" />}
      {artifacts.isError && <ErrorAlert error={artifacts.error} />}
      {artifacts.isSuccess &&
        GROUPS.map((group) => {
          const items = artifacts.data.filter(group.filter)
          return (
            <Stack key={group.title} spacing={1.5} component="section" aria-label={group.title}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="h6" component="h3">
                  {group.title} ({items.length})
                </Typography>
                {group.title !== 'Answered and skipped questions' && (
                  <Button size="small" startIcon={<AddIcon />} onClick={() => setEditing({ type: group.type })}>
                    Add
                  </Button>
                )}
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {group.description}
              </Typography>
              {items.map((a) => (
                <ArtifactCard
                  key={a.key}
                  projectId={projectId}
                  artifact={a}
                  actions={group.actions}
                  onEdit={() => setEditing({ type: a.type, artifact: a })}
                />
              ))}
            </Stack>
          )
        })}
      {editing && (
        <ArtifactEditDialog
          projectId={projectId}
          type={editing.type}
          artifact={editing.artifact}
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  )
}
