import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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

  it('keeps the current draft when starting a new chat', () => {
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Draft question' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'New chat' }))

    expect(screen.getByLabelText('Question')).toHaveValue('Draft question')
  })

  it('keeps the current draft when starting a new chat after messages exist', async () => {
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'What does the roadmap say?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText('Use the roadmap page.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Draft question' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'New chat' }))

    expect(screen.getByLabelText('Question')).toHaveValue('Draft question')
  })

  it('renders source links for the active space route', async () => {
    chatWithSpaceMock.mockResolvedValue({
      answer: 'Read the roadmap page.',
      modelConfigId: 'chat-model',
      summary: null,
      compacted: false,
      sources: [
        {
          path: 'product/roadmap',
          title: 'Product Roadmap',
          excerpt: 'Roadmap details',
        },
      ],
    })
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Where is the roadmap?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    const sources = await screen.findByRole('heading', { name: 'Sources' })
    expect(within(sources.closest('section') as HTMLElement)
      .getByRole('link', { name: 'Product Roadmap' })).toHaveAttribute('href', '/product/roadmap')
  })
})
