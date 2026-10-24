import {
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material'
import { useState } from 'react'
import type { Artifact, FlowNodeType } from '../api/types'
import type { FlowNodeData } from './convert'

export interface NodeDraft extends FlowNodeData {
  type: FlowNodeType
}

const TYPES: FlowNodeType[] = ['start', 'action', 'decision', 'subprocess', 'end']

interface Props {
  title: string
  initial: NodeDraft
  requirements: Artifact[]
  onSave: (draft: NodeDraft) => void
  onDelete?: () => void
  onClose: () => void
}

export default function NodeEditDialog({ title, initial, requirements, onSave, onDelete, onClose }: Props) {
  const [draft, setDraft] = useState(initial)
  const options = requirements.map((r) => r.key)
  const titleOf = (key: string) => requirements.find((r) => r.key === key)?.title ?? key

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          onSave({ ...draft, label: draft.label.trim() })
        }}
      >
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Label"
              value={draft.label}
              onChange={(e) => setDraft({ ...draft, label: e.target.value })}
              required
              autoFocus
              slotProps={{ htmlInput: { maxLength: 120 } }}
            />
            <TextField
              select
              label="Type"
              value={draft.type}
              onChange={(e) => setDraft({ ...draft, type: e.target.value as FlowNodeType })}
            >
              {TYPES.map((t) => (
                <MenuItem key={t} value={t}>
                  {t}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Description"
              value={draft.description ?? ''}
              onChange={(e) => setDraft({ ...draft, description: e.target.value })}
              multiline
              minRows={2}
            />
            <Autocomplete
              multiple
              options={options}
              value={draft.requirementKeys.filter((k) => options.includes(k))}
              onChange={(_, value) => setDraft({ ...draft, requirementKeys: value })}
              getOptionLabel={(key) => `${key}: ${titleOf(key)}`}
              renderInput={(params) => (
                <TextField {...params} label="Implements requirements" helperText="Links used for traceability" />
              )}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          {onDelete && (
            <Button color="error" onClick={onDelete} sx={{ mr: 'auto' }}>
              Delete step
            </Button>
          )}
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={!draft.label.trim()}>
            Done
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
