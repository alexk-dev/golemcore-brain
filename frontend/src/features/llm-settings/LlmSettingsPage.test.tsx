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
const createLlmModelMock = vi.fn()
const updateLlmModelMock = vi.fn()
const deleteLlmModelMock = vi.fn()

vi.mock('../../lib/api', () => ({
  getLlmSettings: (...args: unknown[]) => getLlmSettingsMock(...args),
  createLlmProvider: (...args: unknown[]) => createLlmProviderMock(...args),
  updateLlmProvider: (...args: unknown[]) => updateLlmProviderMock(...args),
  deleteLlmProvider: (...args: unknown[]) => deleteLlmProviderMock(...args),
  checkLlmProvider: (...args: unknown[]) => checkLlmProviderMock(...args),
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

  it('keeps provider secrets hidden and creates embedding model configs', async () => {
    render(<LlmSettingsPage />)

    expect(await screen.findByText((content) => content.includes('secret configured'))).toBeInTheDocument()
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
