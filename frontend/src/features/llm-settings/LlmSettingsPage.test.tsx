import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { LlmSettingsPage } from './LlmSettingsPage'
import { useUiStore } from '../../stores/ui'
import type { LlmSettings } from '../../types'

const getLlmSettingsMock = vi.fn()
const createLlmProviderMock = vi.fn()
const updateLlmProviderMock = vi.fn()
const deleteLlmProviderMock = vi.fn()
const checkLlmProviderMock = vi.fn()
const checkLlmProviderConfigMock = vi.fn()
const checkLlmModelMock = vi.fn()
const createLlmModelMock = vi.fn()
const updateLlmModelMock = vi.fn()
const deleteLlmModelMock = vi.fn()

vi.mock('../../lib/api', () => ({
  getLlmSettings: (...args: unknown[]) => getLlmSettingsMock(...args),
  createLlmProvider: (...args: unknown[]) => createLlmProviderMock(...args),
  updateLlmProvider: (...args: unknown[]) => updateLlmProviderMock(...args),
  deleteLlmProvider: (...args: unknown[]) => deleteLlmProviderMock(...args),
  checkLlmProvider: (...args: unknown[]) => checkLlmProviderMock(...args),
  checkLlmProviderConfig: (...args: unknown[]) => checkLlmProviderConfigMock(...args),
  checkLlmModel: (...args: unknown[]) => checkLlmModelMock(...args),
  createLlmModel: (...args: unknown[]) => createLlmModelMock(...args),
  updateLlmModel: (...args: unknown[]) => updateLlmModelMock(...args),
  deleteLlmModel: (...args: unknown[]) => deleteLlmModelMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

const initialSettings: LlmSettings = {
  providers: {
    openai: {
      apiKey: { value: null, encrypted: false, present: true },
      baseUrl: 'https://api.openai.com/v1',
      requestTimeoutSeconds: 300,
      apiType: 'openai',
    },
  },
  models: [],
}

describe('LlmSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getLlmSettingsMock.mockResolvedValue(initialSettings)
    checkLlmProviderConfigMock.mockResolvedValue({ success: true, message: 'Provider test passed', statusCode: 200 })
    checkLlmModelMock.mockResolvedValue({ success: true, message: 'Model test passed', statusCode: null })
    createLlmModelMock.mockResolvedValue({
      ...initialSettings,
      models: [
        {
          id: 'model-1',
          provider: 'openai',
          modelId: 'text-embedding-3-large',
          displayName: 'Embedding Large',
          kind: 'embedding',
          enabled: true,
          maxInputTokens: null,
          dimensions: 3072,
          temperature: null,
        },
      ],
    })
    useUiStore.setState({
      authDisabled: false,
      currentUser: {
        id: 'admin-1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
    })
  })


  it('fills provider and model defaults as editable values', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    expect(screen.getByLabelText('Base URL')).toHaveValue('https://api.openai.com/v1')
    expect(screen.getByLabelText('Model ID')).toHaveValue('gpt-5.4')

    fireEvent.change(screen.getByLabelText('Kind'), { target: { value: 'embedding' } })

    expect(screen.getByLabelText('Model ID')).toHaveValue('text-embedding-3-large')
  })

  it('can test unsaved provider and model settings from the form', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Test provider' }))
    fireEvent.click(screen.getByRole('button', { name: 'Test model' }))

    await waitFor(() => {
      expect(checkLlmProviderConfigMock).toHaveBeenCalledWith({
        name: 'openai',
        apiKey: null,
        apiType: 'openai',
        baseUrl: 'https://api.openai.com/v1',
        requestTimeoutSeconds: 300,
      })
      expect(checkLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: null,
        kind: 'chat',
        enabled: true,
        maxInputTokens: null,
        dimensions: null,
        temperature: null,
        reasoningEffort: 'medium',
      })
    })
  })


  it('can test saved provider and model settings from their cards', async () => {
    getLlmSettingsMock.mockResolvedValue({
      ...initialSettings,
      models: [
        {
          id: 'chat-model-1',
          provider: 'openai',
          modelId: 'gpt-5.4',
          displayName: 'Reasoning Chat',
          kind: 'chat',
          enabled: true,
          maxInputTokens: null,
          dimensions: null,
          temperature: null,
          reasoningEffort: 'high',
        },
      ],
    })
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Check openai' }))
    fireEvent.click(screen.getByRole('button', { name: 'Test Reasoning Chat' }))

    await waitFor(() => {
      expect(checkLlmProviderMock).toHaveBeenCalledWith('openai')
      expect(checkLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: 'Reasoning Chat',
        kind: 'chat',
        enabled: true,
        maxInputTokens: null,
        dimensions: null,
        temperature: null,
        reasoningEffort: 'high',
      })
    })
  })

  it('creates reasoning chat model configs without temperature', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Chat tuning'), { target: { value: 'reasoning' } })
    fireEvent.change(screen.getByLabelText('Reasoning effort'), { target: { value: 'high' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create model' }))

    await waitFor(() => {
      expect(createLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: null,
        kind: 'chat',
        enabled: true,
        maxInputTokens: null,
        dimensions: null,
        temperature: null,
        reasoningEffort: 'high',
      })
    })
  })

  it('keeps provider secrets hidden and creates embedding model configs', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    expect(screen.getByText('AI setup status')).toBeInTheDocument()
    expect(screen.getByText('Provider connected')).toBeInTheDocument()
    expect(screen.getByText('No enabled chat model')).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('secret configured'))).toBeInTheDocument()
    expect(screen.queryByDisplayValue('sk-secret')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Kind'), { target: { value: 'embedding' } })
    fireEvent.change(screen.getByLabelText('Model ID'), { target: { value: 'text-embedding-3-large' } })
    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Embedding Large' } })
    fireEvent.change(screen.getByLabelText('Dimensions'), { target: { value: '3072' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create model' }))

    await waitFor(() => {
      expect(createLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'text-embedding-3-large',
        displayName: 'Embedding Large',
        kind: 'embedding',
        enabled: true,
        maxInputTokens: null,
        dimensions: 3072,
        temperature: null,
        reasoningEffort: null,
      })
    })
  })

  it('blocks non-admin users in the UI', () => {
    useUiStore.setState({
      authDisabled: false,
      currentUser: {
        id: 'editor-1',
        username: 'editor',
        email: 'editor@example.com',
        role: 'EDITOR',
      },
    })

    render(<LlmSettingsPage />)

    expect(screen.getByText('Admin access required')).toBeInTheDocument()
    expect(getLlmSettingsMock).not.toHaveBeenCalled()
  })
})
