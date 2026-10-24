import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import type { Artifact } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'

interface Props {
  projectId: string
  requirements: Artifact[]
  onCreated: (key: string, warnings: string[]) => void
  onClose: () => void
}

export default function GenerateFlowDialog({ projectId, requirements, onCreated, onClose }: Props) {
  const confirmed = requirements.filter((r) => r.status === 'confirmed')
  const [title, setTitle] = useState('')
  const [selected, setSelected] = useState<string[]>(confirmed.map((r) => r.key))
  const generate = useMutation({
    mutationFn: () => api.generateFlow(projectId, { title: title.trim() || undefined, requirementKeys: selected }),
    onSuccess: (result) => onCreated(result.flow.key, result.warnings),
  })

  return (
    <Dialog open onClose={generate.isPending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>Generate a flow with AI</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {confirmed.length === 0 ? (
            <Alert severity="info">
              Confirm at least one requirement first. Flows are generated only from confirmed requirements.
            </Alert>
          ) : (
            <>
              <TextField
                label="Which journey?"
                placeholder="e.g. Booking flow"
                helperText="Optional. Leave empty to let the AI pick the main journey."
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
              <Typography variant="subtitle2">Use these confirmed requirements</Typography>
              <FormGroup>
                {confirmed.map((r) => (
                  <FormControlLabel
                    key={r.key}
                    control={
                      <Checkbox
                        checked={selected.includes(r.key)}
                        onChange={(e) =>
                          setSelected(e.target.checked ? [...selected, r.key] : selected.filter((k) => k !== r.key))
                        }
                      />
                    }
                    label={`${r.title} (${r.key})`}
                  />
                ))}
              </FormGroup>
              <Typography variant="body2" color="text.secondary">
                The new flow is created as a draft. You can edit it on the canvas.
              </Typography>
            </>
          )}
          {generate.isPending && <Alert severity="info">Generating… this can take up to a minute.</Alert>}
          {generate.isError && <ErrorAlert error={generate.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={generate.isPending}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={() => generate.mutate()}
          disabled={!selected.length || generate.isPending}
        >
          Generate
        </Button>
      </DialogActions>
    </Dialog>
  )
}
