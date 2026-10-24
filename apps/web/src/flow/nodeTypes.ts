import type { NodeTypes } from '@xyflow/react'
import FlowNodeView from './FlowNodes'

export const nodeTypes: NodeTypes = {
  start: FlowNodeView,
  end: FlowNodeView,
  action: FlowNodeView,
  decision: FlowNodeView,
  subprocess: FlowNodeView,
}
