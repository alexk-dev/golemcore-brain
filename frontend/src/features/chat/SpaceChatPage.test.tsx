import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SpaceChatPage } from './SpaceChatPage'
import { useSpaceStore } from '../../stores/space'
import type { LlmModelConfig } from '../../types'

const chatWithSpaceMock = vi.fn()
const getLlmSettingsMock = vi.fn()

vi.mock('../../lib/api', () => ({
  chatWithSpace: (...args: unknown[]) => chatWithSpaceMock(...args),
  getLlmSettings: () => getLlmSettingsMock(),
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
  },
}))

function chatModel(overrides: Partial<LlmModelConfig> & Pick<LlmModelConfig, 'id' | 'modelId'>): LlmModelConfig {
  return {
    provider: 'openai',
    displayName: null,
    kind: 'chat',
    enabled: true,
    supportsTemperature: null,
    maxInputTokens: null,
    dimensions: null,
    temperature: null,
    reasoningEffort: null,
    ...overrides,
  }
}

describe('SpaceChatPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSpaceStore.setState({
      activeSlug: 'docs',
    })
    getLlmSettingsMock.mockResolvedValue({
      providers: {},
      models: [
        chatModel({ id: 'chat-model', modelId: 'gpt-4o', displayName: 'Fast Chat' }),
        chatModel({
          id: 'deep-chat',
          provider: 'anthropic',
          modelId: 'claude-sonnet',
          displayName: 'Deep Chat',
          supportsTemperature: false,
          maxInputTokens: 200000,
        }),
        chatModel({ id: 'disabled-chat', modelId: 'disabled', displayName: 'Disabled Chat', enabled: false }),
        chatModel({ id: 'embedding-model', modelId: 'text-embedding', displayName: 'Embedding', kind: 'embedding' }),
      ],
      modelRegistry: null,
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
    await screen.findByLabelText('Chat model')

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'What does the roadmap say?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(chatWithSpaceMock).toHaveBeenCalledWith('What does the roadmap say?', [], 'chat-model', null, 1)
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
        'chat-model',
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
      expect(chatWithSpaceMock).toHaveBeenLastCalledWith('Start over?', [], 'chat-model', null, 1)
    })
  })

  it('lets users choose among enabled chat models', async () => {
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    const modelSelect = await screen.findByLabelText('Chat model')
    expect(modelSelect).toHaveValue('chat-model')
    expect(screen.getByRole('option', { name: 'Fast Chat' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Deep Chat' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Disabled Chat' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Embedding' })).not.toBeInTheDocument()

    fireEvent.change(modelSelect, { target: { value: 'deep-chat' } })
    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Use the deep model' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => {
      expect(chatWithSpaceMock).toHaveBeenCalledWith('Use the deep model', [], 'deep-chat', null, 1)
    })
  })

  it('keeps the current draft when starting a new chat', async () => {
    render(
      <MemoryRouter>
        <SpaceChatPage />
      </MemoryRouter>,
    )

    await screen.findByLabelText('Chat model')

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

    await screen.findByLabelText('Chat model')

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

    await screen.findByLabelText('Chat model')

    fireEvent.change(screen.getByLabelText('Question'), {
      target: { value: 'Where is the roadmap?' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    const sources = await screen.findByRole('heading', { name: 'Sources' })
    expect(within(sources.closest('section') as HTMLElement)
      .getByRole('link', { name: 'Product Roadmap' })).toHaveAttribute('href', '/product/roadmap')
  })
})
