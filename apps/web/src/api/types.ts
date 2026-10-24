// Mirrors the API DTOs in apps/api (org.openideastudio.*). Keep in sync.

export interface Project {
  id: string
  name: string
  description: string
  currentRevisionId: string | null
  schemaVersion: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProjectSummary {
  project: Project
  counts: {
    confirmedRequirements: number
    openQuestions: number
    flows: number
    screens: number
  }
}

export interface Revision {
  id: string
  projectId: string
  parentRevisionId: string | null
  number: number
  summary: string
  createdBy: string
  affectedArtifacts: string[]
  createdAt: string
}

export interface CreateProjectRequest {
  name: string
  description?: string
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  version: number
}

export type ArtifactType =
  | 'requirement'
  | 'decision'
  | 'assumption'
  | 'question'
  | 'risk'
  | 'suggestion'
  | 'summary'
  | 'flow'
  | 'prototype'

/** Allowed statuses per type; mirrors ArtifactType.java. */
export const ARTIFACT_STATUSES: Record<ArtifactType, string[]> = {
  requirement: ['proposed', 'confirmed', 'needs-clarification', 'deferred', 'rejected'],
  decision: ['proposed', 'confirmed', 'reversed', 'rejected'],
  assumption: ['unconfirmed', 'confirmed', 'rejected'],
  question: ['open', 'answered', 'skipped'],
  risk: ['open', 'mitigated', 'accepted', 'dismissed'],
  suggestion: ['proposed', 'accepted', 'rejected'],
  summary: ['ai-generated', 'edited'],
  flow: ['draft', 'approved', 'archived'],
  prototype: ['draft', 'approved'],
}

export const REQUIREMENT_CATEGORIES = [
  'functional',
  'non-functional',
  'business-rule',
  'security-privacy',
  'operational',
  'out-of-scope',
] as const

export interface ArtifactData {
  description?: string | null
  category?: string | null
  priority?: 'must' | 'should' | 'could' | null
  acceptanceCriteria?: string[] | null
  rationale?: string | null
  impact?: 'low' | 'medium' | 'high' | null
  answer?: string | null
  why?: string | null
  options?: string[] | null
  targetUsers?: string[] | null
  problem?: string | null
  origin?: 'user' | 'ai' | null
  graph?: FlowGraph | null
  prototype?: PrototypeSpec | null
}

export interface Artifact {
  key: string
  type: ArtifactType
  title: string
  status: string
  data: ArtifactData
  version: number
  createdAt: string
  updatedAt: string
}

export interface CreateArtifactRequest {
  type: ArtifactType
  title: string
  status?: string
  data?: ArtifactData
}

export interface UpdateArtifactRequest {
  title?: string
  status?: string
  data?: ArtifactData
  version: number
}

export interface ConversationMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  metadata: {
    model?: string
    createdArtifacts?: string[]
    question?: { key: string | null; text: string; why?: string | null; options?: string[] }
  }
  createdAt: string
}

export interface TurnResult {
  userMessage: ConversationMessage
  assistantMessage: ConversationMessage
  createdArtifacts: Artifact[]
}

export interface AiStatus {
  provider: string
  model: string
  configured: boolean
}

// --- Flows (mirrors org.openideastudio.flow) ---

export type FlowNodeType = 'start' | 'end' | 'action' | 'decision' | 'subprocess'

export interface FlowNode {
  id: string
  type: FlowNodeType
  label: string
  description?: string | null
  position: { x: number; y: number }
  requirementKeys?: string[] | null
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  label?: string | null
}

export interface FlowGraph {
  nodes: FlowNode[]
  edges: FlowEdge[]
  schemaVersion: number
}

export interface FlowOperation {
  op: 'addNode' | 'updateNode' | 'removeNode' | 'addEdge' | 'removeEdge'
  nodeId?: string
  ref?: string
  nodeType?: FlowNodeType
  label?: string
  description?: string
  requirementKeys?: string[]
  from?: string
  to?: string
  edgeId?: string
  edgeLabel?: string
  position?: { x: number; y: number }
}

export interface ChangeSet {
  id: string
  kind: string
  baseRevisionId: string
  targetArtifactKey: string
  targetArtifactVersion: number
  instruction: string | null
  summary: string
  operations: FlowOperation[]
  warnings: string[]
  state: 'pending' | 'applied' | 'rejected' | 'stale'
  createdAt: string
}

export interface FlowSaveResult {
  flow: Artifact
  warnings: string[]
}

export interface FlowProposal {
  changeSet: ChangeSet
  preview: FlowGraph
}

// --- Prototype (mirrors org.openideastudio.prototype) ---

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

export interface PrototypeAction {
  type: 'navigate' | 'message' | 'setState'
  target?: string | null
  message?: string | null
  key?: string | null
  value?: string | null
}

export interface FormField {
  name: string
  label: string
  inputType: 'text' | 'number' | 'date' | 'email' | 'select' | 'textarea' | 'checkbox'
  options?: string[] | null
  required?: boolean | null
  placeholder?: string | null
}

export type PrototypeComponent =
  | { type: 'header'; text: string; subtitle?: string | null }
  | { type: 'text'; text: string; variant?: 'body' | 'caption' | 'heading' | null }
  | { type: 'alert'; text: string; tone?: Tone | null }
  | { type: 'badge'; text: string; tone?: Tone | null }
  | { type: 'button'; label: string; variant?: 'primary' | 'secondary' | 'danger' | null; action: PrototypeAction }
  | { type: 'card'; title?: string | null; body?: string | null; children?: PrototypeComponent[] | null }
  | {
      type: 'list'
      items: {
        title: string
        subtitle?: string | null
        meta?: string | null
        badge?: string | null
        action?: PrototypeAction | null
      }[]
    }
  | { type: 'table'; columns: string[]; rows: string[][] }
  | { type: 'form'; fields: FormField[]; submitLabel: string; action: PrototypeAction }
  | { type: 'tabs'; tabs: { label: string; children: PrototypeComponent[] }[] }
  | {
      type: 'dialog'
      triggerLabel: string
      title: string
      body?: string | null
      confirmLabel: string
      action: PrototypeAction
    }
  | { type: 'image'; alt: string }
  | { type: 'conditional'; key: string; equals: string; children: PrototypeComponent[] }

export interface PrototypeScreen {
  id: string
  title: string
  description?: string | null
  requirementKeys?: string[] | null
  components: PrototypeComponent[]
}

export interface PrototypeSpec {
  title: string
  startScreen: string
  initialState?: Record<string, string> | null
  screens: PrototypeScreen[]
  schemaVersion?: number | null
}

export interface PrototypeDraft {
  spec: PrototypeSpec
  warnings: string[]
  screenId: string | null
}
