import { CircularProgress, List, ListItem, ListItemText, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import ErrorAlert from '../components/ErrorAlert'

export default function RevisionHistorySection({ projectId }: { projectId: string }) {
  const revisions = useQuery({ queryKey: ['revisions', projectId], queryFn: () => api.listRevisions(projectId) })

  return (
    <Stack spacing={2} sx={{ maxWidth: 760 }}>
      <Typography variant="h5" component="h2">
        Version history
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Every saved change creates a revision. Comparing and restoring revisions arrive in Phase 4.
      </Typography>
      {revisions.isPending && <CircularProgress aria-label="Loading revisions" />}
      {revisions.isError && <ErrorAlert error={revisions.error} />}
      {revisions.isSuccess && (
        <List>
          {revisions.data.map((r) => (
            <ListItem key={r.id} divider>
              <ListItemText
                primary={`Revision ${r.number}: ${r.summary}`}
                secondary={`${new Date(r.createdAt).toLocaleString()} · by ${r.createdBy}`}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Stack>
  )
}
