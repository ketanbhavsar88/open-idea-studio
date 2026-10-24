import { Chip, Stack } from '@mui/material'
import type { Artifact } from '../api/types'

const STATUS_COLOR: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  confirmed: 'success',
  answered: 'success',
  mitigated: 'success',
  proposed: 'warning',
  unconfirmed: 'warning',
  open: 'info',
  'needs-clarification': 'info',
  rejected: 'error',
  reversed: 'error',
}

/** Status, origin, and key, so AI suggestions are never mistaken for confirmed facts. */
export default function ArtifactChips({ artifact }: { artifact: Artifact }) {
  return (
    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
      <Chip size="small" label={artifact.status} color={STATUS_COLOR[artifact.status] ?? 'default'} />
      {artifact.data.origin === 'ai' && <Chip size="small" variant="outlined" label="AI suggested" />}
      {artifact.data.priority && <Chip size="small" variant="outlined" label={artifact.data.priority} />}
      {artifact.data.category && <Chip size="small" variant="outlined" label={artifact.data.category} />}
      {artifact.data.impact && <Chip size="small" variant="outlined" label={`${artifact.data.impact} impact`} />}
      <Chip size="small" variant="outlined" label={artifact.key} sx={{ fontFamily: 'monospace' }} />
    </Stack>
  )
}
