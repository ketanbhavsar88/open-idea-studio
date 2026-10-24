import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { PrototypeSpec } from '../api/types'
import PrototypePlayer from './PrototypePlayer'

const spec: PrototypeSpec = {
  title: 'Demo',
  startScreen: 'home',
  initialState: { status: 'none' },
  screens: [
    {
      id: 'home',
      title: 'Home',
      components: [
        { type: 'header', text: 'Vehicles' },
        { type: 'list', items: [{ title: 'Hatchback', meta: '$8/h', action: { type: 'navigate', target: 'book' } }] },
        { type: 'text', text: '<img src=x onerror=alert(1)>' },
      ],
    },
    {
      id: 'book',
      title: 'Book',
      components: [
        { type: 'badge', text: 'Status: {state.status}' },
        {
          type: 'form',
          fields: [{ name: 'start', label: 'Start date', inputType: 'text', required: true }],
          submitLabel: 'Send request',
          action: { type: 'setState', key: 'status', value: 'pending' },
        },
        { type: 'conditional', key: 'status', equals: 'pending', children: [{ type: 'alert', text: 'Waiting for owner' }] },
        { type: 'button', label: 'Pay', action: { type: 'message', message: 'Payment would be taken here' } },
      ],
    },
  ],
}

describe('PrototypePlayer', () => {
  it('renders spec text as text, never as HTML', () => {
    const { container } = render(<PrototypePlayer spec={spec} />)
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText(/PROTOTYPE · simulated/)).toBeInTheDocument()
  })

  it('navigates, validates required fields, updates state, and labels simulated actions', async () => {
    const user = userEvent.setup()
    render(<PrototypePlayer spec={spec} />)
    await user.click(screen.getByText('Hatchback'))
    const frame = screen.getByTestId('prototype-frame')
    expect(within(frame).getByText('Status: none')).toBeInTheDocument()

    await user.click(within(frame).getByRole('button', { name: 'Send request' }))
    expect(within(frame).getByText('Required')).toBeInTheDocument()
    expect(within(frame).queryByText('Waiting for owner')).toBeNull()

    await user.type(within(frame).getByLabelText(/Start date/), 'Friday')
    await user.click(within(frame).getByRole('button', { name: 'Send request' }))
    expect(within(frame).getByText('Status: pending')).toBeInTheDocument()
    expect(within(frame).getByText('Waiting for owner')).toBeInTheDocument()

    await user.click(within(frame).getByRole('button', { name: 'Pay' }))
    expect(await screen.findByText('Simulated: Payment would be taken here')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(within(frame).getByText('Vehicles')).toBeInTheDocument()
  })
})
