import type { Edge, Node } from '@xyflow/react'
import type { FlowGraph, FlowNodeType, FlowOperation } from '../api/types'

export interface FlowNodeData extends Record<string, unknown> {
  label: string
  description?: string | null
  requirementKeys: string[]
  /** Highlights nodes added by an AI proposal in the review preview. */
  highlight?: boolean
}

export type CanvasNode = Node<FlowNodeData, FlowNodeType>
export type CanvasEdge = Edge

export function toCanvas(graph: FlowGraph, highlight: Set<string> = new Set()) {
  const nodes: CanvasNode[] = graph.nodes.map((n) => ({
    id: n.id,
    type: n.type,
    position: { ...n.position },
    data: {
      label: n.label,
      description: n.description ?? null,
      requirementKeys: n.requirementKeys ?? [],
      highlight: highlight.has(n.id),
    },
  }))
  const edges: CanvasEdge[] = graph.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label ?? undefined,
    animated: highlight.has(e.id),
  }))
  return { nodes, edges }
}

/** Back to the stored graph schema; positions are rounded so tiny drags don't create noise. */
export function fromCanvas(nodes: CanvasNode[], edges: CanvasEdge[]): FlowGraph {
  return {
    nodes: nodes.map((n) => ({
      id: n.id,
      type: (n.type ?? 'action') as FlowNodeType,
      label: n.data.label,
      description: n.data.description || null,
      position: { x: Math.round(n.position.x), y: Math.round(n.position.y) },
      requirementKeys: n.data.requirementKeys,
    })),
    edges: edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      label: typeof e.label === 'string' && e.label ? e.label : null,
    })),
    schemaVersion: 1,
  }
}

/** A readable, unique node id derived from a label, like the backend's FlowIds. */
export function newNodeId(label: string, taken: Set<string>): string {
  const slug = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40)
  const base = slug ? `node-${slug}` : 'node'
  let id = base
  for (let n = 2; taken.has(id); n++) id = `${base}-${n}`
  return id
}

export function newEdgeId(source: string, target: string, taken: Set<string>): string {
  const base = `e-${source}-${target}`.replaceAll('node-', '').slice(0, 70)
  let id = base
  for (let n = 2; taken.has(id); n++) id = `${base}-${n}`
  return id
}

/** Plain-language description of an operation for the review dialog. */
export function describeOperation(op: FlowOperation, graph: FlowGraph, preview: FlowGraph): string {
  const label = (id?: string) =>
    preview.nodes.find((n) => n.id === id)?.label ?? graph.nodes.find((n) => n.id === id)?.label ?? id ?? '?'
  switch (op.op) {
    case 'addNode':
      return `Add ${op.nodeType} “${op.label}”`
    case 'updateNode': {
      const changes = [
        op.label && `rename to “${op.label}”`,
        op.nodeType && `change type to ${op.nodeType}`,
        op.description !== undefined && op.description !== null && 'update description',
        op.requirementKeys && `link ${op.requirementKeys.join(', ') || 'no requirements'}`,
      ].filter(Boolean)
      return `Update “${label(op.nodeId)}”: ${changes.join(', ') || 'no visible change'}`
    }
    case 'removeNode':
      return `Remove “${label(op.nodeId)}” and its connections`
    case 'addEdge':
      return `Connect “${label(op.from)}” → “${label(op.to)}”${op.edgeLabel ? ` (${op.edgeLabel})` : ''}`
    case 'removeEdge': {
      const e = graph.edges.find((x) => x.id === op.edgeId)
      return e ? `Disconnect “${label(e.source)}” → “${label(e.target)}”` : `Remove connection ${op.edgeId}`
    }
  }
}
