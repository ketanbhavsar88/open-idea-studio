import AddIcon from '@mui/icons-material/Add'
import RedoIcon from '@mui/icons-material/Redo'
import UndoIcon from '@mui/icons-material/Undo'
import { Alert, Box, Button, IconButton, Menu, MenuItem, Stack, Tooltip, Typography, useTheme } from '@mui/material'
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  useReactFlow,
} from '@xyflow/react'
import type { Connection, EdgeChange, NodeChange } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Artifact, FlowGraph, FlowNodeType } from '../api/types'
import { fromCanvas, newEdgeId, newNodeId, toCanvas } from './convert'
import type { CanvasEdge, CanvasNode } from './convert'
import { nodeTypes } from './nodeTypes'
import NodeEditDialog from './NodeEditDialog'
import type { NodeDraft } from './NodeEditDialog'

interface Snapshot {
  nodes: CanvasNode[]
  edges: CanvasEdge[]
}

interface Props {
  graph: FlowGraph
  requirements: Artifact[]
  /** Reports the current graph and whether it differs from {@link graph}. */
  onChange: (graph: FlowGraph, dirty: boolean) => void
}

const HISTORY_LIMIT = 100

/**
 * Editable canvas over the stored graph schema. Every structural edit and every
 * completed drag is an undo step; nothing is saved until the parent saves.
 */
export default function FlowEditor({ graph, requirements, onChange }: Props) {
  const theme = useTheme()
  const flow = useReactFlow()
  const initial = useMemo(() => toCanvas(graph), [graph])
  const savedJson = useMemo(() => JSON.stringify(fromCanvas(initial.nodes, initial.edges)), [initial])
  const [nodes, setNodes] = useState<CanvasNode[]>(initial.nodes)
  const [edges, setEdges] = useState<CanvasEdge[]>(initial.edges)
  const [past, setPast] = useState<Snapshot[]>([])
  const [future, setFuture] = useState<Snapshot[]>([])
  const [editing, setEditing] = useState<{ id: string | null; draft: NodeDraft } | null>(null)
  const [addMenu, setAddMenu] = useState<HTMLElement | null>(null)
  const dragStart = useRef<Snapshot | null>(null)

  useEffect(() => {
    const current = fromCanvas(nodes, edges)
    onChange(current, JSON.stringify(current) !== savedJson)
  }, [nodes, edges, savedJson, onChange])

  const commit = useCallback(
    (next: Snapshot, before: Snapshot = { nodes, edges }) => {
      setPast((p) => [...p.slice(-HISTORY_LIMIT + 1), before])
      setFuture([])
      setNodes(next.nodes)
      setEdges(next.edges)
    },
    [nodes, edges],
  )

  const undo = useCallback(() => {
    const prev = past.at(-1)
    if (!prev) return
    setPast((p) => p.slice(0, -1))
    setFuture((f) => [{ nodes, edges }, ...f])
    setNodes(prev.nodes)
    setEdges(prev.edges)
  }, [past, nodes, edges])

  const redo = useCallback(() => {
    const next = future[0]
    if (!next) return
    setFuture((f) => f.slice(1))
    setPast((p) => [...p, { nodes, edges }])
    setNodes(next.nodes)
    setEdges(next.edges)
  }, [future, nodes, edges])

  const onNodesChange = useCallback(
    (changes: NodeChange<CanvasNode>[]) => {
      const structural = changes.some((c) => c.type === 'remove')
      const next = applyNodeChanges(changes, nodes)
      if (structural) {
        const removed = new Set(changes.flatMap((c) => (c.type === 'remove' ? [c.id] : [])))
        commit({ nodes: next, edges: edges.filter((e) => !removed.has(e.source) && !removed.has(e.target)) })
        return
      }
      for (const c of changes) {
        if (c.type === 'position' && c.dragging && !dragStart.current) dragStart.current = { nodes, edges }
        if (c.type === 'position' && c.dragging === false && dragStart.current) {
          commit({ nodes: next, edges }, dragStart.current)
          dragStart.current = null
          return
        }
      }
      setNodes(next)
    },
    [nodes, edges, commit],
  )

  const onEdgesChange = useCallback(
    (changes: EdgeChange<CanvasEdge>[]) => {
      const next = applyEdgeChanges(changes, edges)
      if (changes.some((c) => c.type === 'remove')) commit({ nodes, edges: next })
      else setEdges(next)
    },
    [nodes, edges, commit],
  )

  const onConnect = useCallback(
    (c: Connection) => {
      if (c.source === c.target) return
      const id = newEdgeId(c.source, c.target, new Set(edges.map((e) => e.id)))
      commit({ nodes, edges: addEdge({ ...c, id }, edges) })
    },
    [nodes, edges, commit],
  )

  const addNode = (type: FlowNodeType) => {
    setAddMenu(null)
    setEditing({ id: null, draft: { type, label: '', description: '', requirementKeys: [] } })
  }

  const saveNode = (draft: NodeDraft) => {
    const { type, ...data } = draft
    if (editing?.id) {
      commit({
        nodes: nodes.map((n) => (n.id === editing.id ? { ...n, type, data: { ...n.data, ...data } } : n)),
        edges,
      })
    } else {
      const id = newNodeId(draft.label, new Set(nodes.map((n) => n.id)))
      const center = flow.screenToFlowPosition({ x: window.innerWidth / 2, y: window.innerHeight / 2 })
      commit({ nodes: [...nodes, { id, type, position: center, data }], edges })
    }
    setEditing(null)
  }

  const deleteNode = (id: string) => {
    commit({ nodes: nodes.filter((n) => n.id !== id), edges: edges.filter((e) => e.source !== id && e.target !== id) })
    setEditing(null)
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.key.toLowerCase() !== 'z') return
      if ((e.target as HTMLElement)?.closest('input, textarea')) return
      e.preventDefault()
      if (e.shiftKey) redo()
      else undo()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [undo, redo])

  return (
    <Stack spacing={1} sx={{ height: '100%' }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
        <Button size="small" startIcon={<AddIcon />} onClick={(e) => setAddMenu(e.currentTarget)}>
          Add step
        </Button>
        <Menu anchorEl={addMenu} open={!!addMenu} onClose={() => setAddMenu(null)}>
          {(['action', 'decision', 'subprocess', 'start', 'end'] as FlowNodeType[]).map((t) => (
            <MenuItem key={t} onClick={() => addNode(t)}>
              {t}
            </MenuItem>
          ))}
        </Menu>
        <Tooltip title="Undo (Ctrl+Z)">
          <span>
            <IconButton size="small" aria-label="Undo" onClick={undo} disabled={!past.length}>
              <UndoIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Redo (Ctrl+Shift+Z)">
          <span>
            <IconButton size="small" aria-label="Redo" onClick={redo} disabled={!future.length}>
              <RedoIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Typography variant="caption" color="text.secondary">
          Drag to move · drag from the bottom dot to connect · double-click to edit · select and press Delete to remove
        </Typography>
      </Stack>
      {nodes.length === 0 && <Alert severity="info">This flow is empty. Add a step to begin.</Alert>}
      <Box sx={{ flexGrow: 1, minHeight: 420, border: 1, borderColor: 'divider', borderRadius: 1 }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeDoubleClick={(_, n) =>
            setEditing({
              id: n.id,
              draft: { type: (n.type ?? 'action') as FlowNodeType, ...n.data, description: n.data.description ?? '' },
            })
          }
          deleteKeyCode={['Backspace', 'Delete']}
          colorMode={theme.palette.mode}
          defaultEdgeOptions={{ type: 'smoothstep' }}
          fitView
          proOptions={{ hideAttribution: false }}
        >
          <Background />
          <Controls />
          <MiniMap pannable zoomable />
        </ReactFlow>
      </Box>
      {editing && (
        <NodeEditDialog
          title={editing.id ? 'Edit step' : `Add ${editing.draft.type}`}
          initial={editing.draft}
          requirements={requirements}
          onSave={saveNode}
          onDelete={editing.id ? () => deleteNode(editing.id!) : undefined}
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  )
}

/** Read-only canvas, used to preview AI proposals. */
export function FlowPreview({ graph, highlight }: { graph: FlowGraph; highlight: Set<string> }) {
  const theme = useTheme()
  const { nodes, edges } = useMemo(() => toCanvas(graph, highlight), [graph, highlight])
  return (
    <Box sx={{ height: 360, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        colorMode={theme.palette.mode}
        defaultEdgeOptions={{ type: 'smoothstep' }}
        fitView
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </Box>
  )
}
