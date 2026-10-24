import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import type { FormField, PrototypeAction, PrototypeComponent, Tone } from '../api/types'
import { interpolate } from './interpolate'

/**
 * Trusted renderer for prototype specs (design doc §12). Specs are untrusted
 * data: every string is rendered as React text (never HTML), there are no
 * links, URLs, images or scripts, and every action is handled by the player.
 */

export interface RenderContext {
  state: Record<string, string>
  run: (action: PrototypeAction) => void
}

const TONE_SEVERITY: Record<Tone, 'info' | 'success' | 'warning' | 'error'> = {
  neutral: 'info',
  info: 'info',
  success: 'success',
  warning: 'warning',
  danger: 'error',
}
const TONE_CHIP: Record<Tone, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  neutral: 'default',
  info: 'info',
  success: 'success',
  warning: 'warning',
  danger: 'error',
}

export function RenderComponents({ components, ctx }: { components: PrototypeComponent[]; ctx: RenderContext }) {
  return (
    <Stack spacing={2}>
      {components.map((c, i) => (
        <RenderComponent key={i} component={c} ctx={ctx} />
      ))}
    </Stack>
  )
}

function RenderComponent({ component: c, ctx }: { component: PrototypeComponent; ctx: RenderContext }) {
  const t = (s: string | null | undefined) => (s ? interpolate(s, ctx.state) : '')
  switch (c.type) {
    case 'header':
      return (
        <Box>
          <Typography variant="h5" component="h2">
            {t(c.text)}
          </Typography>
          {c.subtitle && <Typography color="text.secondary">{t(c.subtitle)}</Typography>}
        </Box>
      )
    case 'text':
      return (
        <Typography
          variant={c.variant === 'caption' ? 'caption' : c.variant === 'heading' ? 'h6' : 'body1'}
          color={c.variant === 'caption' ? 'text.secondary' : undefined}
          sx={{ whiteSpace: 'pre-wrap' }}
        >
          {t(c.text)}
        </Typography>
      )
    case 'alert':
      return <Alert severity={TONE_SEVERITY[c.tone ?? 'info']}>{t(c.text)}</Alert>
    case 'badge':
      return (
        <Box>
          <Chip label={t(c.text)} color={TONE_CHIP[c.tone ?? 'neutral']} size="small" />
        </Box>
      )
    case 'button':
      return (
        <Box>
          <Button
            variant={c.variant === 'secondary' ? 'outlined' : 'contained'}
            color={c.variant === 'danger' ? 'error' : 'primary'}
            onClick={() => ctx.run(c.action)}
          >
            {t(c.label)}
          </Button>
        </Box>
      )
    case 'card':
      return (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={1.5}>
              {c.title && (
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  {t(c.title)}
                </Typography>
              )}
              {c.body && <Typography variant="body2">{t(c.body)}</Typography>}
              {c.children && c.children.length > 0 && <RenderComponents components={c.children} ctx={ctx} />}
            </Stack>
          </CardContent>
        </Card>
      )
    case 'list':
      return (
        <List disablePadding sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
          {c.items.map((item, i) => (
            <ListItemButton
              key={i}
              divider={i < c.items.length - 1}
              disabled={!item.action}
              onClick={() => item.action && ctx.run(item.action)}
              sx={{ '&.Mui-disabled': { opacity: 1 } }}
            >
              <ListItemText primary={t(item.title)} secondary={item.subtitle ? t(item.subtitle) : undefined} />
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                {item.badge && <Chip size="small" label={t(item.badge)} />}
                {item.meta && (
                  <Typography variant="body2" color="text.secondary">
                    {t(item.meta)}
                  </Typography>
                )}
              </Stack>
            </ListItemButton>
          ))}
        </List>
      )
    case 'table':
      return (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                {c.columns.map((col, i) => (
                  <TableCell key={i}>{t(col)}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {c.rows.map((row, r) => (
                <TableRow key={r}>
                  {row.map((cell, i) => (
                    <TableCell key={i}>{t(cell)}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )
    case 'form':
      return <RenderForm fields={c.fields} submitLabel={c.submitLabel} action={c.action} ctx={ctx} />
    case 'tabs':
      return <RenderTabs tabs={c.tabs} ctx={ctx} />
    case 'dialog':
      return <RenderDialog component={c} ctx={ctx} />
    case 'image':
      return (
        <Box
          role="img"
          aria-label={c.alt}
          sx={{
            height: 140,
            bgcolor: 'action.hover',
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            display: 'grid',
            placeItems: 'center',
            color: 'text.secondary',
          }}
        >
          <Typography variant="caption">Image: {c.alt}</Typography>
        </Box>
      )
    case 'conditional':
      return ctx.state[c.key] === c.equals ? <RenderComponents components={c.children} ctx={ctx} /> : null
  }
}

function RenderForm(props: { fields: FormField[]; submitLabel: string; action: PrototypeAction; ctx: RenderContext }) {
  const { fields, submitLabel, action, ctx } = props
  const [values, setValues] = useState<Record<string, string>>({})
  const [submitted, setSubmitted] = useState(false)
  const missing = fields.filter((f) => f.required && !values[f.name])

  return (
    <Box
      component="form"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        setSubmitted(true)
        if (missing.length === 0) ctx.run(action)
      }}
    >
      <Stack spacing={2}>
        {fields.map((f) =>
          f.inputType === 'checkbox' ? (
            <FormControlLabel
              key={f.name}
              control={
                <Checkbox
                  checked={values[f.name] === 'true'}
                  onChange={(e) => setValues({ ...values, [f.name]: e.target.checked ? 'true' : '' })}
                />
              }
              label={f.label + (f.required ? ' *' : '')}
            />
          ) : (
            <TextField
              key={f.name}
              label={f.label}
              required={!!f.required}
              select={f.inputType === 'select'}
              multiline={f.inputType === 'textarea'}
              minRows={f.inputType === 'textarea' ? 3 : undefined}
              type={['number', 'date', 'email'].includes(f.inputType) ? f.inputType : 'text'}
              placeholder={f.placeholder ?? undefined}
              value={values[f.name] ?? ''}
              onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}
              error={submitted && !!f.required && !values[f.name]}
              helperText={submitted && f.required && !values[f.name] ? 'Required' : undefined}
              slotProps={f.inputType === 'date' ? { inputLabel: { shrink: true } } : undefined}
            >
              {(f.options ?? []).map((o) => (
                <MenuItem key={o} value={o}>
                  {o}
                </MenuItem>
              ))}
            </TextField>
          ),
        )}
        <Box>
          <Button type="submit" variant="contained">
            {submitLabel}
          </Button>
        </Box>
      </Stack>
    </Box>
  )
}

function RenderTabs({ tabs, ctx }: { tabs: { label: string; children: PrototypeComponent[] }[]; ctx: RenderContext }) {
  const [index, setIndex] = useState(0)
  const current = tabs[Math.min(index, tabs.length - 1)]
  return (
    <Box>
      <Tabs value={Math.min(index, tabs.length - 1)} onChange={(_, v) => setIndex(v)} variant="scrollable">
        {tabs.map((tab, i) => (
          <Tab key={i} label={interpolate(tab.label, ctx.state)} />
        ))}
      </Tabs>
      <Box sx={{ pt: 2 }}>
        <RenderComponents components={current.children} ctx={ctx} />
      </Box>
    </Box>
  )
}

function RenderDialog({
  component: c,
  ctx,
}: {
  component: Extract<PrototypeComponent, { type: 'dialog' }>
  ctx: RenderContext
}) {
  const [open, setOpen] = useState(false)
  return (
    <Box>
      <Button variant="outlined" onClick={() => setOpen(true)}>
        {interpolate(c.triggerLabel, ctx.state)}
      </Button>
      {/* disablePortal keeps the dialog inside the preview frame. */}
      <Dialog open={open} onClose={() => setOpen(false)} disablePortal>
        <DialogTitle>{interpolate(c.title, ctx.state)}</DialogTitle>
        {c.body && (
          <DialogContent>
            <Typography>{interpolate(c.body, ctx.state)}</Typography>
          </DialogContent>
        )}
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              setOpen(false)
              ctx.run(c.action)
            }}
          >
            {interpolate(c.confirmLabel, ctx.state)}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
