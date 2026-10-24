import AddIcon from '@mui/icons-material/Add'
import { Box, Button, Chip, CircularProgress, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { useArtifacts } from '../api/hooks'
import { ARTIFACT_STATUSES } from '../api/types'
import type { Artifact } from '../api/types'
import ArtifactCard from '../components/ArtifactCard'
import ArtifactEditDialog from '../components/ArtifactEditDialog'
import ErrorAlert from '../components/ErrorAlert'

const ACTIONS = [
  { label: 'Confirm', status: 'confirmed' },
  { label: 'Needs clarification', status: 'needs-clarification' },
  { label: 'Defer', status: 'deferred' },
  { label: 'Reject', status: 'rejected' },
]

export default function RequirementsSection({ projectId }: { projectId: string }) {
  const artifacts = useArtifacts(projectId)
  const [filter, setFilter] = useState<string | null>(null)
  const [editing, setEditing] = useState<Artifact | 'new' | null>(null)
  const requirements = (artifacts.data ?? []).filter((a) => a.type === 'requirement')
  const shown = filter ? requirements.filter((r) => r.status === filter) : requirements

  return (
    <Stack spacing={2} sx={{ maxWidth: 900 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5" component="h2">
          Requirements
        </Typography>
        <Button startIcon={<AddIcon />} onClick={() => setEditing('new')}>
          Add requirement
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        AI suggestions start as <em>proposed</em>. Nothing becomes confirmed until you confirm it.
      </Typography>
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }} role="group" aria-label="Filter by status">
        <Chip label={`All (${requirements.length})`} color={filter === null ? 'primary' : 'default'} onClick={() => setFilter(null)} />
        {ARTIFACT_STATUSES.requirement.map((s) => {
          const count = requirements.filter((r) => r.status === s).length
          return (
            <Chip key={s} label={`${s} (${count})`} color={filter === s ? 'primary' : 'default'} onClick={() => setFilter(s)} />
          )
        })}
      </Stack>
      {artifacts.isPending && <CircularProgress aria-label="Loading requirements" />}
      {artifacts.isError && <ErrorAlert error={artifacts.error} />}
      {artifacts.isSuccess && shown.length === 0 && (
        <Box sx={{ py: 4, textAlign: 'center', color: 'text.secondary' }}>
          {requirements.length === 0
            ? 'No requirements yet. Discuss your idea with the AI, or add one yourself.'
            : 'No requirements with this status.'}
        </Box>
      )}
      {shown.map((r) => (
        <ArtifactCard key={r.key} projectId={projectId} artifact={r} actions={ACTIONS} onEdit={() => setEditing(r)} />
      ))}
      {editing && (
        <ArtifactEditDialog
          projectId={projectId}
          type="requirement"
          artifact={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  )
}
