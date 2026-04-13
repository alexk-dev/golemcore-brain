import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SpaceChatPage } from './SpaceChatPage'
import { useSpaceStore } from '../../stores/space'

const chatWithSpaceMock = vi.fn()

vi.mock('../../lib/api', () => ({
  chatWithSpace: (...args: unknown[]) => chatWithSpaceMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
  },
}))

describe('SpaceChatPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSpaceStore.setState({
      activeSlug: 'docs',
    })
    chatWithSpaceMock.mockResolvedValue({
      answer: 'Use the roadmap page.',
      modelConfigId: 'chat-model',
      summary: 'Roadmap question summary.',
      compacted: true,
      sources: [],
    })
  })

  it('sends the compact summary and resets it for a new chat', async () => {
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: 'New chat' })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'What does the roadmap say?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(chatWithSpaceMock).toHaveBeenCalledWith('What does the roadmap say?', [], undefined, null, 1)
    })
    expect(await screen.findByText('Use the roadmap page.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'What changed?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(chatWithSpaceMock).toHaveBeenLastCalledWith(
        'What changed?',
        [
          { role: 'user', content: 'What does the roadmap say?' },
          { role: 'assistant', content: 'Use the roadmap page.' },
        ],
        undefined,
        'Roadmap question summary.',
        2,
      )
    })

    fireEvent.click(screen.getByRole('button', { name: 'New chat' }))
    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Start over?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(chatWithSpaceMock).toHaveBeenLastCalledWith('Start over?', [], undefined, null, 1)
    })
  })
})
