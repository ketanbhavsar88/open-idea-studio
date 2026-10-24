import AddIcon from '@mui/icons-material/Add'
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined'
import {
  Box,
  Button,
  Card,
  CardActionArea,
  CardActions,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  IconButton,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { api } from '../api/client'
import type { Project, ProjectSummary } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'
import CreateProjectDialog from './CreateProjectDialog'

export default function ProjectsDashboard() {
  const [creating, setCreating] = useState(false)
  const [deleting, setDeleting] = useState<Project | null>(null)
  const projects = useQuery({ queryKey: ['projects'], queryFn: api.listProjects })

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 4, justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h4" component="h1">
            Open Idea Studio
          </Typography>
          <Typography color="text.secondary">
            Turn an idea into a clear product plan, visual flows, and a clickable prototype.
          </Typography>
        </Box>
        <Box>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            New project
          </Button>
        </Box>
      </Stack>

      {projects.isPending && <CircularProgress aria-label="Loading projects" />}
      {projects.isError && <ErrorAlert error={projects.error} />}
      {projects.isSuccess && projects.data.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography variant="h6">No projects yet</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            Start with a sentence or two about your idea.
          </Typography>
          <Button variant="outlined" onClick={() => setCreating(true)}>
            Create your first project
          </Button>
        </Box>
      )}
      {projects.isSuccess && projects.data.length > 0 && (
        <Grid container spacing={2}>
          {projects.data.map((summary) => (
            <Grid key={summary.project.id} size={{ xs: 12, sm: 6, md: 4 }}>
              <ProjectCard summary={summary} onDelete={() => setDeleting(summary.project)} />
            </Grid>
          ))}
        </Grid>
      )}

      {creating && <CreateProjectDialog open onClose={() => setCreating(false)} />}
      {deleting && <DeleteProjectDialog project={deleting} onClose={() => setDeleting(null)} />}
    </Container>
  )
}

function ProjectCard({ summary, onDelete }: { summary: ProjectSummary; onDelete: () => void }) {
  const { project, counts } = summary
  return (
    <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardActionArea component={RouterLink} to={`/projects/${project.id}`} sx={{ flexGrow: 1 }}>
        <CardContent>
          <Typography variant="h6" component="h2" noWrap>
            {project.name}
          </Typography>
          <Typography
            color="text.secondary"
            variant="body2"
            sx={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden', minHeight: 60 }}
          >
            {project.description || 'No description yet.'}
          </Typography>
          <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, flexWrap: 'wrap' }}>
            <Chip size="small" label={`${counts.confirmedRequirements} confirmed requirements`} />
            <Chip size="small" label={`${counts.openQuestions} open questions`} />
            <Chip size="small" label={`${counts.flows} flows`} />
          </Stack>
        </CardContent>
      </CardActionArea>
      <CardActions sx={{ justifyContent: 'space-between' }}>
        <Typography variant="caption" color="text.secondary">
          Updated {new Date(project.updatedAt).toLocaleString()}
        </Typography>
        <IconButton aria-label={`Delete ${project.name}`} onClick={onDelete} size="small">
          <DeleteOutlinedIcon fontSize="small" />
        </IconButton>
      </CardActions>
    </Card>
  )
}

function DeleteProjectDialog({ project, onClose }: { project: Project; onClose: () => void }) {
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => api.deleteProject(project.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      onClose()
    },
  })
  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Delete “{project.name}”?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          This permanently deletes the project, its artifacts, and its revision history.
        </DialogContentText>
        {remove.isError && <ErrorAlert error={remove.error} />}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" variant="contained" onClick={() => remove.mutate()} disabled={remove.isPending}>
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  )
}
