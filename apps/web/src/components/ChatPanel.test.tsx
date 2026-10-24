import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ConversationMessage } from '../api/types'
import ChatPanel from './ChatPanel'

const assistant: ConversationMessage = {
  id: 'm2',
  role: 'assistant',
  content: 'A vehicle sharing marketplace.',
  metadata: {
    createdArtifacts: ['REQ-OWNERS-CAN-LIST-A-VEHICLE'],
    question: { key: 'Q-1', text: 'Does the owner approve each booking?', why: 'Pending state.', options: ['Yes', 'No'] },
  },
  createdAt: '2026-10-24T10:00:00Z',
}

function setup(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const handler = routes[`${init?.method ?? 'GET'} ${url}`]
    return Promise.resolve(handler ? handler() : new Response(null, { status: 404 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ChatPanel projectId="p1" />
    </QueryClientProvider>,
  )
  return fetchMock
}

describe('ChatPanel', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the question, why it matters, options, and proposed items', async () => {
    setup({
      'GET /api/v1/projects/p1/messages': () => Response.json([assistant]),
      'GET /api/v1/ai/status': () => Response.json({ provider: 'gemini', model: 'gemma', configured: true }),
    })
    expect(await screen.findByText('Does the owner approve each booking?')).toBeInTheDocument()
    expect(screen.getByText('Why it matters: Pending state.')).toBeInTheDocument()
    expect(screen.getByText('Skip for now')).toBeInTheDocument()
    expect(screen.getByText(/Proposed for your review: REQ-OWNERS-CAN-LIST-A-VEHICLE/)).toBeInTheDocument()
  })

  it('keeps the message and offers retry when the model is rate limited', async () => {
    const userMsg: ConversationMessage = { id: 'm3', role: 'user', content: 'Yes', metadata: {}, createdAt: '' }
    let sent = false
    setup({
      'GET /api/v1/projects/p1/messages': () => Response.json(sent ? [assistant, userMsg] : [assistant]),
      'GET /api/v1/ai/status': () => Response.json({ provider: 'gemini', model: 'gemma', configured: true }),
      'POST /api/v1/projects/p1/messages': () => {
        sent = true
        return Response.json({ code: 'model-rate-limited', detail: 'Rate limit reached.' }, { status: 429 })
      },
    })
    await userEvent.click(await screen.findByText('Yes'))
    expect(await screen.findByText(/Rate limit reached\. Your message was saved/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('warns when the provider is not configured', async () => {
    setup({
      'GET /api/v1/projects/p1/messages': () => Response.json([]),
      'GET /api/v1/ai/status': () => Response.json({ provider: 'gemini', model: 'gemma', configured: false }),
    })
    expect(await screen.findByText(/is not configured/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start discovery' })).toBeInTheDocument()
  })
})
