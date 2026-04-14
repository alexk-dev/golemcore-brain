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
const resolveModelRegistryMock = vi.fn()

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
  resolveModelRegistry: (...args: unknown[]) => resolveModelRegistryMock(...args),
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
      legacyApi: false,
    },
  },
  models: [],
  modelRegistry: null,
}

describe('LlmSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getLlmSettingsMock.mockResolvedValue(initialSettings)
    checkLlmProviderMock.mockResolvedValue({
      success: true,
      message: 'Provider responded to model listing (2 models)',
      statusCode: 200,
      models: ['gpt-5.4', 'text-embedding-3-large'],
    })
    checkLlmProviderConfigMock.mockResolvedValue({
      success: true,
      message: 'Provider responded to model listing (2 models)',
      statusCode: 200,
      models: ['gpt-5.4', 'text-embedding-3-large'],
    })
    checkLlmModelMock.mockResolvedValue({ success: true, message: 'Model test passed', statusCode: null })
    resolveModelRegistryMock.mockResolvedValue({
      defaultSettings: null,
      configSource: null,
      cacheStatus: 'miss',
    })
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

  it('opens provider and model create forms in modal dialogs', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Base URL')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Add provider' }))

    expect(screen.getByRole('dialog', { name: 'Create provider' })).toBeInTheDocument()
    expect(screen.getByLabelText('Base URL')).toHaveValue('https://api.openai.com/v1')

    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))

    expect(screen.queryByLabelText('Model ID')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))

    expect(screen.getByRole('dialog', { name: 'Create model' })).toBeInTheDocument()
    expect(screen.getByLabelText('Model ID')).toHaveValue('gpt-5.4')
  })

  it('separates providers and models into tabs and keeps save available after edit', async () => {
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
    expect(screen.getByRole('tab', { name: /Providers/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: 'Edit openai provider settings' })).toBeInTheDocument()
    expect(screen.queryByText('Reasoning Chat')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit openai provider settings' }))

    expect(screen.getByRole('dialog', { name: 'Edit provider: openai' })).toBeInTheDocument()
    expect(screen.getByLabelText('Provider ID')).toBeDisabled()
    expect(screen.getByLabelText('OpenAI chat API')).toHaveValue('responses')
    expect(screen.getByPlaceholderText('Secret is configured (hidden)')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Save provider' })).toBeInTheDocument()
    expect(screen.getByText('Save the provider to apply a new API key.'))
      .toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))

    expect(screen.getByRole('tab', { name: /Models/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('Reasoning Chat')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save provider' })).not.toBeInTheDocument()
  })


  it('fills provider and model defaults as editable values', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Add provider' }))
    expect(screen.getByLabelText('Base URL')).toHaveValue('https://api.openai.com/v1')
    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))

    expect(screen.getByLabelText('Model ID')).toHaveValue('gpt-5.4')

    fireEvent.change(screen.getByLabelText('Kind'), { target: { value: 'embedding' } })

    expect(screen.getByLabelText('Model ID')).toHaveValue('text-embedding-3-large')
  })

  it('can test unsaved provider and model settings from the form', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Add provider' }))
    fireEvent.change(screen.getByLabelText('OpenAI chat API'), { target: { value: 'chat_completions' } })
    fireEvent.click(screen.getByRole('button', { name: 'Test provider' }))
    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))
    fireEvent.click(screen.getByRole('button', { name: 'Test model' }))

    await waitFor(() => {
      expect(checkLlmProviderConfigMock).toHaveBeenCalledWith({
        name: 'openai',
        apiKey: null,
        apiType: 'openai',
        baseUrl: 'https://api.openai.com/v1',
        requestTimeoutSeconds: 300,
        legacyApi: true,
      })
      expect(checkLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: null,
        kind: 'chat',
        enabled: true,
        supportsTemperature: false,
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

    fireEvent.click(screen.getByRole('button', { name: 'Detect openai models' }))
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Test Reasoning Chat' }))

    await waitFor(() => {
      expect(checkLlmProviderMock).toHaveBeenCalledWith('openai')
      expect(checkLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: 'Reasoning Chat',
        kind: 'chat',
        enabled: true,
        supportsTemperature: false,
        maxInputTokens: null,
        dimensions: null,
        temperature: null,
        reasoningEffort: 'high',
      })
    })
  })

  it('opens detected model picker and copies selected model defaults', async () => {
    resolveModelRegistryMock.mockResolvedValue({
      defaultSettings: {
        provider: 'openai',
        displayName: 'GPT 5.4',
        supportsVision: null,
        supportsTemperature: false,
        maxInputTokens: 128000,
        reasoning: {
          default: 'high',
          levels: {},
        },
      },
      configSource: 'remote',
      cacheStatus: 'hit',
    })
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))
    fireEvent.click(screen.getByRole('button', { name: 'Detect models' }))

    expect(await screen.findByRole('dialog', { name: 'Detected models for openai' })).toBeInTheDocument()
    expect(screen.getByLabelText('Filter detected models')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Filter detected models'), { target: { value: '5.4' } })
    expect(screen.getByRole('button', { name: 'Select gpt-5.4' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Select text-embedding-3-large' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Select gpt-5.4' }))

    await waitFor(() => {
      expect(resolveModelRegistryMock).toHaveBeenCalledWith('openai', 'gpt-5.4')
    })
    expect(screen.queryByRole('dialog', { name: 'Detected models for openai' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Model ID')).toHaveValue('gpt-5.4')
    expect(screen.getByLabelText('Display name')).toHaveValue('GPT 5.4')
    expect(screen.getByLabelText('Max input tokens')).toHaveValue(128000)
    expect(screen.getByLabelText('Chat tuning')).toHaveValue('reasoning')
    expect(screen.getByLabelText('Reasoning effort')).toHaveValue('high')
  })

  it('creates reasoning chat model configs with custom effort values', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'AI Models' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))
    fireEvent.change(screen.getByLabelText('Chat tuning'), { target: { value: 'reasoning' } })
    expect(screen.getByLabelText('Reasoning effort')).toHaveAttribute('list', 'llm-reasoning-efforts')
    fireEvent.change(screen.getByLabelText('Reasoning effort'), { target: { value: 'minimal' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create model' }))

    await waitFor(() => {
      expect(createLlmModelMock).toHaveBeenCalledWith({
        provider: 'openai',
        modelId: 'gpt-5.4',
        displayName: null,
        kind: 'chat',
        enabled: true,
        supportsTemperature: false,
        maxInputTokens: null,
        dimensions: null,
        temperature: null,
        reasoningEffort: 'minimal',
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

    fireEvent.click(screen.getByRole('tab', { name: /Models/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Add model' }))
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
        supportsTemperature: null,
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
