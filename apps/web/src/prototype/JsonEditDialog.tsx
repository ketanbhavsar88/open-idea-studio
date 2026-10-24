import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import type { Artifact, PrototypeSpec } from '../api/types'
import ErrorAlert from '../components/ErrorAlert'

interface Props {
  projectId: string
  current: Artifact | null
  onSaved: (warnings: string[]) => void
  onClose: () => void
}

const STARTER: PrototypeSpec = {
  title: 'My prototype',
  startScreen: 'home',
  initialState: {},
  screens: [{ id: 'home', title: 'Home', requirementKeys: [], components: [{ type: 'header', text: 'Welcome' }] }],
  schemaVersion: 1,
}

/** Manual editing of the spec; works without any AI. The backend validates on save. */
export default function JsonEditDialog({ projectId, current, onSaved, onClose }: Props) {
  const [text, setText] = useState(JSON.stringify(current?.data.prototype ?? STARTER, null, 2))
  const [parseError, setParseError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: (spec: PrototypeSpec) =>
      api.savePrototype(projectId, { spec, version: current?.version ?? null, fromAi: false }),
    onSuccess: (result) => onSaved(result.warnings),
  })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Edit prototype specification</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            The spec uses a fixed set of components and simulated actions; see docs/data-model.md. It is validated
            when you save.
          </Typography>
          <TextField
            multiline
            minRows={18}
            maxRows={30}
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              setParseError(null)
            }}
            slotProps={{ htmlInput: { spellCheck: false, 'aria-label': 'Prototype JSON' } }}
            sx={{ '& textarea': { fontFamily: 'monospace', fontSize: 13 } }}
          />
          {parseError && <Alert severity="error">Not valid JSON: {parseError}</Alert>}
          {save.isError && <ErrorAlert error={save.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={save.isPending}
          onClick={() => {
            try {
              save.mutate(JSON.parse(text) as PrototypeSpec)
            } catch (e) {
              setParseError(e instanceof Error ? e.message : String(e))
            }
          }}
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  )
}
