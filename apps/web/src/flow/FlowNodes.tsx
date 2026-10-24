import { Box, Typography } from '@mui/material'
import { Handle, Position } from '@xyflow/react'
import type { NodeProps } from '@xyflow/react'
import type { CanvasNode } from './convert'

const STYLE: Record<string, { border: string; radius: number | string; tag: string }> = {
  start: { border: 'success.main', radius: 999, tag: 'Start' },
  end: { border: 'error.main', radius: 999, tag: 'End' },
  action: { border: 'primary.main', radius: 1, tag: 'Step' },
  decision: { border: 'warning.main', radius: 1, tag: 'Decision' },
  subprocess: { border: 'secondary.main', radius: 1, tag: 'Sub-process' },
}

export default function FlowNodeView({ type, data, selected }: NodeProps<CanvasNode>) {
  const s = STYLE[type] ?? STYLE.action
  return (
    <Box
      sx={{
        minWidth: 160,
        maxWidth: 220,
        px: 1.5,
        py: 1,
        bgcolor: 'background.paper',
        color: 'text.primary',
        border: 2,
        borderColor: s.border,
        borderStyle: type === 'decision' ? 'dashed' : 'solid',
        borderRadius: s.radius,
        outline: type === 'subprocess' ? 2 : 0,
        outlineOffset: 2,
        outlineColor: 'secondary.main',
        outlineStyle: 'solid',
        boxShadow: selected ? 4 : data.highlight ? '0 0 0 4px rgba(46,125,50,.45)' : 0,
        textAlign: 'center',
      }}
    >
      <Handle type="target" position={Position.Top} />
      <Typography variant="caption" color="text.secondary" component="div" sx={{ lineHeight: 1.2 }}>
        {s.tag}
        {data.requirementKeys.length > 0 && ` · ${data.requirementKeys.length} req`}
        {data.highlight && ' · new'}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600, wordBreak: 'break-word' }}>
        {data.label}
      </Typography>
      <Handle type="source" position={Position.Bottom} />
    </Box>
  )
}
