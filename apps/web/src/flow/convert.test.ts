import { describe, expect, it } from 'vitest'
import type { FlowGraph } from '../api/types'
import { describeOperation, fromCanvas, newEdgeId, newNodeId, toCanvas } from './convert'

const graph: FlowGraph = {
  nodes: [
    { id: 'node-start', type: 'start', label: 'Start', position: { x: 0, y: 0 }, requirementKeys: [] },
    { id: 'node-pay', type: 'action', label: 'Pay', position: { x: 10.4, y: 140 }, requirementKeys: ['REQ-PAY'] },
  ],
  edges: [{ id: 'e1', source: 'node-start', target: 'node-pay', label: null }],
  schemaVersion: 1,
}

describe('flow conversion', () => {
  it('round-trips the stored graph without changing ids', () => {
    const { nodes, edges } = toCanvas(graph)
    const back = fromCanvas(nodes, edges)
    expect(back.nodes.map((n) => n.id)).toEqual(['node-start', 'node-pay'])
    expect(back.nodes[1].position).toEqual({ x: 10, y: 140 })
    expect(back.nodes[1].requirementKeys).toEqual(['REQ-PAY'])
    expect(back.edges).toEqual([{ id: 'e1', source: 'node-start', target: 'node-pay', label: null }])
  })

  it('generates unique readable ids', () => {
    expect(newNodeId('Owner approves?', new Set())).toBe('node-owner-approves')
    expect(newNodeId('Pay', new Set(['node-pay']))).toBe('node-pay-2')
    expect(newEdgeId('node-a', 'node-b', new Set(['e-a-b']))).toBe('e-a-b-2')
  })

  it('describes operations in plain language', () => {
    expect(describeOperation({ op: 'removeEdge', edgeId: 'e1' }, graph, graph)).toBe('Disconnect “Start” → “Pay”')
    expect(describeOperation({ op: 'addNode', nodeType: 'decision', label: 'Approve?' }, graph, graph)).toBe(
      'Add decision “Approve?”',
    )
    expect(describeOperation({ op: 'addEdge', from: 'node-start', to: 'node-pay', edgeLabel: 'Yes' }, graph, graph)).toBe(
      'Connect “Start” → “Pay” (Yes)',
    )
  })
})
