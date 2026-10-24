import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ProjectSummary } from '../api/types'
import ProjectsDashboard from './ProjectsDashboard'

function renderWithResponse(response: Response | Error) {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => (response instanceof Error ? Promise.reject(response) : Promise.resolve(response))),
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ProjectsDashboard />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const summary: ProjectSummary = {
  project: {
    id: 'p1',
    name: 'Vehicle sharing',
    description: 'Rent unused private vehicles for a few hours.',
    currentRevisionId: 'r1',
    schemaVersion: 1,
    version: 1,
    createdAt: '2026-10-24T10:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z',
  },
  counts: { confirmedRequirements: 3, openQuestions: 2, flows: 1, screens: 0 },
}

describe('ProjectsDashboard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists projects with transparent progress counts', async () => {
    renderWithResponse(Response.json([summary]))
    expect(await screen.findByText('Vehicle sharing')).toBeInTheDocument()
    expect(screen.getByText('3 confirmed requirements')).toBeInTheDocument()
    expect(screen.getByText('2 open questions')).toBeInTheDocument()
  })

  it('shows an empty state', async () => {
    renderWithResponse(Response.json([]))
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
  })

  it('shows an actionable error when the API is unreachable', async () => {
    renderWithResponse(new TypeError('Failed to fetch'))
    expect(await screen.findByText(/Cannot reach the Open Idea Studio API/)).toBeInTheDocument()
  })
})
