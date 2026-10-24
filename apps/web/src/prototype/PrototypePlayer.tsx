import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RestartAltIcon from '@mui/icons-material/RestartAlt'
import {
  Alert,
  Box,
  IconButton,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import type { PrototypeAction, PrototypeSpec } from '../api/types'
import { RenderComponents } from './renderer'

interface Props {
  spec: PrototypeSpec
  /** Start somewhere other than the spec's start screen (e.g. a screen under review). */
  initialScreen?: string
}

/**
 * Click-through preview. All behaviour is local and simulated: navigation,
 * state changes, and notices. Nothing leaves the browser.
 */
export default function PrototypePlayer({ spec, initialScreen }: Props) {
  const start = initialScreen ?? spec.startScreen
  const [screenId, setScreenId] = useState(start)
  const [history, setHistory] = useState<string[]>([])
  const [state, setState] = useState<Record<string, string>>(spec.initialState ?? {})
  const [notice, setNotice] = useState<string | null>(null)
  const [viewport, setViewport] = useState<'desktop' | 'mobile'>('desktop')

  const screen = spec.screens.find((s) => s.id === screenId) ?? spec.screens[0]

  const go = (target: string) => {
    if (target === screen.id || !spec.screens.some((s) => s.id === target)) return
    setHistory((h) => [...h, screen.id])
    setScreenId(target)
  }

  const run = (action: PrototypeAction) => {
    switch (action.type) {
      case 'navigate':
        if (action.target) go(action.target)
        break
      case 'message':
        setNotice(action.message ?? 'Done')
        break
      case 'setState':
        if (action.key) setState((s) => ({ ...s, [action.key!]: action.value ?? '' }))
        if (action.target) go(action.target)
        else setNotice(`State updated: ${action.key} = ${action.value}`)
        break
    }
  }

  const reset = () => {
    setScreenId(start)
    setHistory([])
    setState(spec.initialState ?? {})
    setNotice(null)
  }

  return (
    <Stack spacing={1.5}>
      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Tooltip title="Back">
          <span>
            <IconButton
              aria-label="Back"
              size="small"
              disabled={!history.length}
              onClick={() => {
                setScreenId(history.at(-1)!)
                setHistory((h) => h.slice(0, -1))
              }}
            >
              <ArrowBackIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Restart prototype">
          <IconButton aria-label="Restart prototype" size="small" onClick={reset}>
            <RestartAltIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <TextField
          select
          size="small"
          label="Screen"
          value={screen.id}
          onChange={(e) => go(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          {spec.screens.map((s) => (
            <MenuItem key={s.id} value={s.id}>
              {s.title}
            </MenuItem>
          ))}
        </TextField>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={viewport}
          onChange={(_, v) => v && setViewport(v)}
          aria-label="Viewport"
        >
          <ToggleButton value="desktop">Desktop</ToggleButton>
          <ToggleButton value="mobile">Mobile</ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      <Box sx={{ display: 'flex', justifyContent: 'center', bgcolor: 'action.hover', p: { xs: 1, sm: 2 }, borderRadius: 1 }}>
        <Paper
          variant="outlined"
          data-testid="prototype-frame"
          sx={{
            width: '100%',
            maxWidth: viewport === 'mobile' ? 390 : 1100,
            minHeight: 480,
            position: 'relative',
            overflow: 'hidden',
            // Keep in-prototype dialogs inside the frame.
            transform: 'translateZ(0)',
          }}
        >
          <Box
            sx={{
              px: 2,
              py: 0.75,
              bgcolor: 'warning.light',
              color: 'warning.contrastText',
              display: 'flex',
              justifyContent: 'space-between',
              gap: 1,
            }}
          >
            <Typography variant="caption" sx={{ fontWeight: 600 }}>
              PROTOTYPE · simulated data and actions
            </Typography>
            <Typography variant="caption">{screen.title}</Typography>
          </Box>
          <Box sx={{ p: { xs: 2, sm: 3 } }}>
            <RenderComponents key={screen.id} components={screen.components} ctx={{ state, run }} />
          </Box>
        </Paper>
      </Box>

      <Snackbar
        open={!!notice}
        autoHideDuration={4000}
        // Clicking another control must not dismiss the notice it just raised.
        onClose={(_, reason) => reason !== 'clickaway' && setNotice(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="info" onClose={() => setNotice(null)} variant="filled">
          Simulated: {notice}
        </Alert>
      </Snackbar>
    </Stack>
  )
}
