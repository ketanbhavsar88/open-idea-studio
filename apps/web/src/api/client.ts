import type {
  AiStatus,
  Artifact,
  ChangeSet,
  FlowGraph,
  FlowProposal,
  FlowSaveResult,
  ConversationMessage,
  CreateArtifactRequest,
  CreateProjectRequest,
  Project,
  ProjectSummary,
  Revision,
  TurnResult,
  UpdateArtifactRequest,
  UpdateProjectRequest,
} from './types'

/** An RFC 9457 problem returned by the API, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>
  readonly errors: string[]

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}, errors: string[] = []) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
    this.errors = errors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError(0, 'network', 'Cannot reach the Open Idea Studio API. Is it running?')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.code ?? 'unknown',
      problem.detail ?? `Request failed (${response.status}).`,
      problem.fields,
      problem.errors,
    )
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export const api = {
  listProjects: () => request<ProjectSummary[]>('/api/v1/projects'),
  getProject: (id: string) => request<Project>(`/api/v1/projects/${id}`),
  createProject: (body: CreateProjectRequest) =>
    request<Project>('/api/v1/projects', { method: 'POST', body: JSON.stringify(body) }),
  updateProject: (id: string, body: UpdateProjectRequest) =>
    request<Project>(`/api/v1/projects/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteProject: (id: string) => request<void>(`/api/v1/projects/${id}`, { method: 'DELETE' }),
  listRevisions: (projectId: string) =>
    request<Revision[]>(`/api/v1/projects/${projectId}/revisions`),

  aiStatus: () => request<AiStatus>('/api/v1/ai/status'),

  listArtifacts: (projectId: string) => request<Artifact[]>(`/api/v1/projects/${projectId}/artifacts`),
  createArtifact: (projectId: string, body: CreateArtifactRequest) =>
    request<Artifact>(`/api/v1/projects/${projectId}/artifacts`, { method: 'POST', body: JSON.stringify(body) }),
  updateArtifact: (projectId: string, key: string, body: UpdateArtifactRequest) =>
    request<Artifact>(`/api/v1/projects/${projectId}/artifacts/${encodeURIComponent(key)}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  listMessages: (projectId: string) =>
    request<ConversationMessage[]>(`/api/v1/projects/${projectId}/messages`),
  sendMessage: (projectId: string, content: string) =>
    request<TurnResult>(`/api/v1/projects/${projectId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),
  startDiscovery: (projectId: string) =>
    request<TurnResult>(`/api/v1/projects/${projectId}/messages/start`, { method: 'POST' }),
  listFlows: (projectId: string) => request<Artifact[]>(`/api/v1/projects/${projectId}/flows`),
  createFlow: (projectId: string, title: string) =>
    request<Artifact>(`/api/v1/projects/${projectId}/flows`, { method: 'POST', body: JSON.stringify({ title }) }),
  saveFlow: (projectId: string, key: string, body: { title?: string; graph: FlowGraph; version: number }) =>
    request<FlowSaveResult>(`/api/v1/projects/${projectId}/flows/${encodeURIComponent(key)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  generateFlow: (projectId: string, body: { title?: string; requirementKeys?: string[] }) =>
    request<FlowSaveResult>(`/api/v1/projects/${projectId}/flows/generate`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  proposeFlowChange: (projectId: string, key: string, instruction: string) =>
    request<FlowProposal>(`/api/v1/projects/${projectId}/flows/${encodeURIComponent(key)}/proposals`, {
      method: 'POST',
      body: JSON.stringify({ instruction }),
    }),
  pendingFlowProposals: (projectId: string, key: string) =>
    request<ChangeSet[]>(`/api/v1/projects/${projectId}/flows/${encodeURIComponent(key)}/proposals`),
  getChangeSet: (projectId: string, id: string) =>
    request<FlowProposal>(`/api/v1/projects/${projectId}/changesets/${id}`),
  applyChangeSet: (projectId: string, id: string) =>
    request<{ changeSet: ChangeSet; flow: Artifact; warnings: string[] }>(
      `/api/v1/projects/${projectId}/changesets/${id}/apply`,
      { method: 'POST' },
    ),
  rejectChangeSet: (projectId: string, id: string) =>
    request<ChangeSet>(`/api/v1/projects/${projectId}/changesets/${id}/reject`, { method: 'POST' }),

  retryReply: (projectId: string) =>
    request<TurnResult>(`/api/v1/projects/${projectId}/messages/retry`, { method: 'POST' }),
}
