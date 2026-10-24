import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import ErrorAlert from '../components/ErrorAlert'

export default function CreateProjectDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState('')
  const [idea, setIdea] = useState('')
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const create = useMutation({
    mutationFn: () => api.createProject({ name, description: idea }),
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      navigate(`/projects/${project.id}`)
    },
  })
  const fieldErrors = create.error instanceof ApiError ? create.error.fields : {}

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          create.mutate()
        }}
      >
        <DialogTitle>New project</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Project name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
              slotProps={{ htmlInput: { maxLength: 200 } }}
              error={!!fieldErrors.name}
              helperText={fieldErrors.name}
            />
            <TextField
              label="Describe your idea"
              placeholder="An app where people can rent unused private vehicles from owners for a few hours."
              helperText="Rough and incomplete is fine. You can refine it later."
              value={idea}
              onChange={(e) => setIdea(e.target.value)}
              multiline
              minRows={4}
            />
            {create.isError && !fieldErrors.name && <ErrorAlert error={create.error} />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={!name.trim() || create.isPending}>
            {create.isPending ? 'Creating…' : 'Create project'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
