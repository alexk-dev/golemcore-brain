import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'

import { ModalCard } from '../../components/ModalCard'
import {
  checkLlmProvider,
  checkLlmProviderConfig,
  checkLlmModel,
  createLlmModel,
  createLlmProvider,
  deleteLlmModel,
  deleteLlmProvider,
  getLlmSettings,
  updateLlmModel,
  updateLlmProvider,
} from '../../lib/api'
import { useUiStore } from '../../stores/ui'
import type {
  LlmApiType,
  LlmModelConfig,
  LlmModelKind,
  LlmProviderCheckResult,
  LlmSettings,
  SaveLlmModelPayload,
  SaveLlmProviderPayload,
  Secret,
} from '../../types'

interface ProviderFormState {
  name: string
  apiType: LlmApiType
  baseUrl: string
  requestTimeoutSeconds: string
  apiKey: string
}

interface ModelFormState {
  provider: string
  kind: LlmModelKind
  modelId: string
  displayName: string
  enabled: boolean
  maxInputTokens: string
  dimensions: string
  temperature: string
  chatTuning: 'temperature' | 'reasoning'
  reasoningEffort: 'low' | 'medium' | 'high'
}

type LlmSettingsTab = 'providers' | 'models'
type LlmSettingsModal = 'provider' | 'model' | null

const EMPTY_SETTINGS: LlmSettings = { providers: {}, models: [] }
const API_TYPES: LlmApiType[] = ['openai', 'anthropic', 'gemini']
const MODEL_KINDS: LlmModelKind[] = ['chat', 'embedding']
const DEFAULT_PROVIDER_NAME = 'openai'
const DEFAULT_CHAT_MODEL_ID = 'gpt-5.4'
const DEFAULT_EMBEDDING_MODEL_ID = 'text-embedding-3-large'
const REASONING_EFFORTS = ['low', 'medium', 'high'] as const

const KNOWN_BASE_URLS: Record<string, string> = {
  openai: 'https://api.openai.com/v1',
  openrouter: 'https://openrouter.ai/api/v1',
  anthropic: 'https://api.anthropic.com',
  google: 'https://generativelanguage.googleapis.com/v1beta',
  gemini: 'https://generativelanguage.googleapis.com/v1beta',
  groq: 'https://api.groq.com/openai/v1',
  deepseek: 'https://api.deepseek.com/v1',
  mistral: 'https://api.mistral.ai/v1',
  xai: 'https://api.x.ai/v1',
}

function emptyProviderForm(): ProviderFormState {
  return {
    name: DEFAULT_PROVIDER_NAME,
    apiType: 'openai',
    baseUrl: KNOWN_BASE_URLS[DEFAULT_PROVIDER_NAME],
    requestTimeoutSeconds: '300',
    apiKey: '',
  }
}

function emptyModelForm(provider = ''): ModelFormState {
  return {
    provider,
    kind: 'chat',
    modelId: DEFAULT_CHAT_MODEL_ID,
    displayName: '',
    enabled: true,
    maxInputTokens: '',
    dimensions: '',
    temperature: '',
    chatTuning: 'reasoning',
    reasoningEffort: 'medium',
  }
}

function normalizeProviderName(value: string): string {
  return value.trim().toLowerCase()
}

function toNullableString(value: string): string | null {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function toNullableNumber(value: string): number | null {
  const trimmed = value.trim()
  if (trimmed.length === 0) return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : null
}

function toSecret(value: string): Secret | null {
  const trimmed = value.trim()
  if (trimmed.length === 0) return null
  return { value: trimmed, encrypted: false, present: true }
}

function defaultApiTypeForProvider(name: string): LlmApiType {
  if (name === 'anthropic') return 'anthropic'
  if (name === 'google' || name === 'gemini') return 'gemini'
  return 'openai'
}

function modelLabel(model: LlmModelConfig): string {
  return model.displayName?.trim() || model.modelId
}

export function LlmSettingsPage() {
  const currentUser = useUiStore((state) => state.currentUser)
  const isAdmin = currentUser?.role === 'ADMIN'

  const [settings, setSettings] = useState<LlmSettings>(EMPTY_SETTINGS)
  const [loading, setLoading] = useState(true)
  const [savingProvider, setSavingProvider] = useState(false)
  const [savingModel, setSavingModel] = useState(false)
  const [editingProvider, setEditingProvider] = useState<string | null>(null)
  const [providerForm, setProviderForm] = useState<ProviderFormState>(emptyProviderForm)
  const [editingModelId, setEditingModelId] = useState<string | null>(null)
  const [modelForm, setModelForm] = useState<ModelFormState>(emptyModelForm)
  const [checkingProvider, setCheckingProvider] = useState<string | null>(null)
  const [checkingProviderForm, setCheckingProviderForm] = useState(false)
  const [checkingModelForm, setCheckingModelForm] = useState(false)
  const [checkingSavedModelId, setCheckingSavedModelId] = useState<string | null>(null)
  const [checkResults, setCheckResults] = useState<Record<string, LlmProviderCheckResult>>({})
  const [modelCheckResults, setModelCheckResults] = useState<Record<string, LlmProviderCheckResult>>({})
  const [providerFormCheckResult, setProviderFormCheckResult] = useState<LlmProviderCheckResult | null>(null)
  const [modelFormCheckResult, setModelFormCheckResult] = useState<LlmProviderCheckResult | null>(null)
  const [activeTab, setActiveTab] = useState<LlmSettingsTab>('providers')
  const [activeModal, setActiveModal] = useState<LlmSettingsModal>(null)

  const providerNames = useMemo(() => Object.keys(settings.providers).sort(), [settings.providers])
  const enabledChatModels = useMemo(
    () => settings.models.filter((model) => model.kind === 'chat' && model.enabled),
    [settings.models],
  )
  const enabledEmbeddingModels = useMemo(
    () => settings.models.filter((model) => model.kind === 'embedding' && model.enabled),
    [settings.models],
  )
  const hasConnectedProvider = providerNames.some((name) => settings.providers[name]?.apiKey?.present)

  useEffect(() => {
    if (!isAdmin) {
      setLoading(false)
      return
    }
    setLoading(true)
    void getLlmSettings()
      .then((response) => setSettings(response))
      .catch((error: Error) => toast.error(error.message))
      .finally(() => setLoading(false))
  }, [isAdmin])

  useEffect(() => {
    if (modelForm.provider.length === 0 && providerNames.length > 0) {
      setModelForm((state) => ({ ...state, provider: providerNames[0] }))
    }
  }, [modelForm.provider.length, providerNames])

  if (!isAdmin) {
    return (
      <div className="shell-form-page">
        <div className="shell-form-page__card--wide surface-card p-6">
          <h2 className="mb-2 text-xl font-semibold">Admin access required</h2>
          <p className="text-sm text-muted">You must be signed in as an administrator to manage LLM settings.</p>
        </div>
      </div>
    )
  }

  const resetProviderForm = () => {
    setEditingProvider(null)
    setProviderForm(emptyProviderForm())
  }

  const resetModelForm = () => {
    setEditingModelId(null)
    setModelForm(emptyModelForm(providerNames[0] ?? ''))
  }

  const closeProviderModal = () => {
    resetProviderForm()
    setActiveModal(null)
  }

  const closeModelModal = () => {
    resetModelForm()
    setActiveModal(null)
  }

  const handleAddProvider = () => {
    resetProviderForm()
    setProviderFormCheckResult(null)
    setActiveModal('provider')
  }

  const handleAddModel = () => {
    resetModelForm()
    setModelFormCheckResult(null)
    setActiveModal('model')
  }

  const handleProviderNameChange = (value: string) => {
    const name = normalizeProviderName(value)
    const apiType = defaultApiTypeForProvider(name)
    setProviderForm((state) => ({
      ...state,
      name,
      apiType,
      baseUrl: KNOWN_BASE_URLS[name] ?? state.baseUrl,
    }))
  }

  const handleProviderSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const name = normalizeProviderName(providerForm.name)
    if (!editingProvider && name.length === 0) {
      toast.error('Provider name is required')
      return
    }
    const payload: SaveLlmProviderPayload = {
      ...buildProviderPayload(),
      ...(editingProvider ? {} : { name }),
    }
    setSavingProvider(true)
    try {
      const response = editingProvider
        ? await updateLlmProvider(editingProvider, payload)
        : await createLlmProvider(payload)
      setSettings(response)
      toast.success(editingProvider ? 'Provider updated' : 'Provider created')
      resetProviderForm()
      setActiveModal(null)
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSavingProvider(false)
    }
  }

  const buildProviderPayload = (): SaveLlmProviderPayload => {
    const secret = toSecret(providerForm.apiKey)
    return {
      name: normalizeProviderName(providerForm.name),
      ...(secret ? { apiKey: secret } : { apiKey: null }),
      apiType: providerForm.apiType,
      baseUrl: toNullableString(providerForm.baseUrl),
      requestTimeoutSeconds: toNullableNumber(providerForm.requestTimeoutSeconds),
    }
  }

  const buildModelPayload = (): SaveLlmModelPayload => {
    const isEmbedding = modelForm.kind === 'embedding'
    const isReasoning = modelForm.kind === 'chat' && modelForm.chatTuning === 'reasoning'
    return {
      provider: modelForm.provider,
      modelId: modelForm.modelId.trim(),
      displayName: toNullableString(modelForm.displayName),
      kind: modelForm.kind,
      enabled: modelForm.enabled,
      maxInputTokens: toNullableNumber(modelForm.maxInputTokens),
      dimensions: isEmbedding ? toNullableNumber(modelForm.dimensions) : null,
      temperature: modelForm.kind === 'chat' && !isReasoning ? toNullableNumber(modelForm.temperature) : null,
      reasoningEffort: isReasoning ? modelForm.reasoningEffort : null,
    }
  }

  const handleProviderFormCheck = async () => {
    const payload = buildProviderPayload()
    if (!payload.name) {
      toast.error('Provider name is required')
      return
    }
    setCheckingProviderForm(true)
    setProviderFormCheckResult(null)
    try {
      const result = await checkLlmProviderConfig(payload)
      setProviderFormCheckResult(result)
      if (result.success) {
        toast.success(result.message)
      } else {
        toast.error(result.message)
      }
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setCheckingProviderForm(false)
    }
  }

  const toModelPayload = (model: LlmModelConfig): SaveLlmModelPayload => ({
    provider: model.provider,
    modelId: model.modelId,
    displayName: model.displayName,
    kind: model.kind,
    enabled: model.enabled,
    maxInputTokens: model.maxInputTokens,
    dimensions: model.dimensions,
    temperature: model.temperature,
    reasoningEffort: model.reasoningEffort,
  })

  const handleModelFormCheck = async () => {
    const payload = buildModelPayload()
    if (payload.provider.length === 0 || payload.modelId.length === 0) {
      toast.error('Provider and model ID are required')
      return
    }
    setCheckingModelForm(true)
    setModelFormCheckResult(null)
    try {
      const result = await checkLlmModel(payload)
      setModelFormCheckResult(result)
      if (result.success) {
        toast.success(result.message)
      } else {
        toast.error(result.message)
      }
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setCheckingModelForm(false)
    }
  }

  const handleModelKindChange = (kind: LlmModelKind) => {
    setModelForm((state) => ({
      ...state,
      kind,
      modelId: kind === 'embedding' ? DEFAULT_EMBEDDING_MODEL_ID : DEFAULT_CHAT_MODEL_ID,
    }))
  }

  const handleEditProvider = (name: string) => {
    const provider = settings.providers[name]
    if (!provider) return
    setActiveTab('providers')
    setActiveModal('provider')
    setProviderFormCheckResult(null)
    setEditingProvider(name)
    setProviderForm({
      name,
      apiType: provider.apiType ?? defaultApiTypeForProvider(name),
      baseUrl: provider.baseUrl ?? '',
      requestTimeoutSeconds: String(provider.requestTimeoutSeconds ?? 300),
      apiKey: '',
    })
  }

  const handleDeleteProvider = async (name: string) => {
    if (!window.confirm(`Delete provider ${name}?`)) return
    try {
      const response = await deleteLlmProvider(name)
      setSettings(response)
      if (editingProvider === name) resetProviderForm()
      toast.success('Provider deleted')
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleCheckProvider = async (name: string) => {
    setCheckingProvider(name)
    try {
      const result = await checkLlmProvider(name)
      setCheckResults((state) => ({ ...state, [name]: result }))
      if (result.success) {
        toast.success(result.message)
      } else {
        toast.error(result.message)
      }
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setCheckingProvider(null)
    }
  }

  const handleModelSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (modelForm.provider.length === 0) {
      toast.error('Provider is required')
      return
    }
    if (modelForm.modelId.trim().length === 0) {
      toast.error('Model ID is required')
      return
    }
    const payload = buildModelPayload()
    setSavingModel(true)
    try {
      const response = editingModelId
        ? await updateLlmModel(editingModelId, payload)
        : await createLlmModel(payload)
      setSettings(response)
      toast.success(editingModelId ? 'Model updated' : 'Model created')
      resetModelForm()
      setActiveModal(null)
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSavingModel(false)
    }
  }

  const handleEditModel = (model: LlmModelConfig) => {
    setActiveTab('models')
    setActiveModal('model')
    setModelFormCheckResult(null)
    setEditingModelId(model.id)
    setModelForm({
      provider: model.provider,
      kind: model.kind,
      modelId: model.modelId,
      displayName: model.displayName ?? '',
      enabled: model.enabled,
      maxInputTokens: model.maxInputTokens == null ? '' : String(model.maxInputTokens),
      dimensions: model.dimensions == null ? '' : String(model.dimensions),
      temperature: model.temperature == null ? '' : String(model.temperature),
      chatTuning: model.reasoningEffort ? 'reasoning' : 'temperature',
      reasoningEffort: model.reasoningEffort ?? 'medium',
    })
  }

  const handleCheckModel = async (model: LlmModelConfig) => {
    setCheckingSavedModelId(model.id)
    setModelCheckResults((state) => {
      const nextState = { ...state }
      delete nextState[model.id]
      return nextState
    })
    try {
      const result = await checkLlmModel(toModelPayload(model))
      setModelCheckResults((state) => ({ ...state, [model.id]: result }))
      if (result.success) {
        toast.success(result.message)
      } else {
        toast.error(result.message)
      }
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setCheckingSavedModelId(null)
    }
  }

  const handleDeleteModel = async (model: LlmModelConfig) => {
    if (!window.confirm(`Delete model config ${modelLabel(model)}?`)) return
    try {
      const response = await deleteLlmModel(model.id)
      setSettings(response)
      if (editingModelId === model.id) resetModelForm()
      toast.success('Model deleted')
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-1 text-2xl font-semibold">AI Models</h1>
        <p className="mb-6 text-sm text-muted">
          Connect provider credentials once, then expose enabled chat models inside each space's Dynamic APIs.
          Saved secrets stay server-side and are never returned to this screen.
        </p>

        {loading ? (
          <div className="text-sm text-muted">Loading settings...</div>
        ) : (
          <div className="space-y-8">
            <section className="rounded-lg border border-surface-border bg-surface-alt/60 px-4 py-3">
              <h2 className="mb-3 text-lg font-semibold">AI setup status</h2>
              <div className="grid gap-3 text-sm md:grid-cols-3">
                <div className="rounded-lg bg-background px-3 py-2">
                  <div className="font-medium">{hasConnectedProvider ? 'Provider connected' : 'Provider missing'}</div>
                  <div className="text-xs text-muted">API keys stay hidden after save.</div>
                </div>
                <div className="rounded-lg bg-background px-3 py-2">
                  <div className="font-medium">
                    {enabledChatModels.length > 0 ? `${enabledChatModels.length} enabled chat model` : 'No enabled chat model'}
                  </div>
                  <div className="text-xs text-muted">Required for Dynamic APIs.</div>
                </div>
                <div className="rounded-lg bg-background px-3 py-2">
                  <div className="font-medium">
                    {enabledEmbeddingModels.length > 0
                      ? `${enabledEmbeddingModels.length} enabled embedding model`
                      : 'No enabled embedding model'}
                  </div>
                  <div className="text-xs text-muted">Optional for semantic search quality.</div>
                </div>
              </div>
            </section>

            <div className="flex flex-wrap gap-2 border-b border-surface-border" role="tablist" aria-label="AI settings sections">
              <button
                type="button"
                id="llm-providers-tab"
                role="tab"
                aria-selected={activeTab === 'providers'}
                aria-controls="llm-providers-panel"
                className={activeTab === 'providers' ? 'border-b-2 border-accent px-4 py-3 text-sm font-semibold text-accent' : 'px-4 py-3 text-sm font-medium text-muted hover:text-foreground'}
                onClick={() => setActiveTab('providers')}
              >
                Providers ({providerNames.length})
              </button>
              <button
                type="button"
                id="llm-models-tab"
                role="tab"
                aria-selected={activeTab === 'models'}
                aria-controls="llm-models-panel"
                className={activeTab === 'models' ? 'border-b-2 border-accent px-4 py-3 text-sm font-semibold text-accent' : 'px-4 py-3 text-sm font-medium text-muted hover:text-foreground'}
                onClick={() => setActiveTab('models')}
              >
                Models ({settings.models.length})
              </button>
            </div>

            {activeTab === 'providers' ? (
              <section role="tabpanel" id="llm-providers-panel" aria-labelledby="llm-providers-tab">
                <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex flex-col gap-1">
                    <h2 className="text-lg font-semibold">Providers</h2>
                    <p className="text-sm text-muted">Create a provider or edit an existing provider credential.</p>
                  </div>
                  <button type="button" className="action-button-primary" onClick={handleAddProvider}>
                    Add provider
                  </button>
                </div>

              {providerNames.length === 0 ? (
                <div className="text-sm text-muted">No LLM providers configured.</div>
              ) : (
                <div className="space-y-3">
                  {providerNames.map((name) => {
                    const provider = settings.providers[name]
                    const checkResult = checkResults[name]
                    return (
                      <div key={name} className="rounded-lg border border-surface-border bg-surface-alt/60 px-4 py-3">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="font-medium">{name}</div>
                            <div className="text-sm text-muted">
                              <span>{provider.apiType ?? 'openai'}</span>
                              <span> · {provider.baseUrl ?? 'default endpoint'}</span>
                              <span> · {provider.apiKey?.present ? 'secret configured' : 'secret missing'}</span>
                            </div>
                            {checkResult ? (
                              <div className={checkResult.success ? 'mt-2 text-sm text-accent' : 'mt-2 text-sm text-danger'}>
                                {checkResult.message}{checkResult.statusCode ? ` (${checkResult.statusCode})` : ''}
                              </div>
                            ) : null}
                          </div>
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              className="action-button-secondary"
                              aria-label={`Check ${name}`}
                              onClick={() => void handleCheckProvider(name)}
                              disabled={checkingProvider === name}
                            >
                              {checkingProvider === name ? 'Checking...' : 'Check'}
                            </button>
                            <button
                              type="button"
                              className="action-button-secondary"
                              aria-label={`Edit ${name} provider settings`}
                              onClick={() => handleEditProvider(name)}
                            >
                              Edit
                            </button>
                            <button type="button" className="action-button-secondary" onClick={() => void handleDeleteProvider(name)}>
                              Delete
                            </button>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
              </section>
            ) : null}

            {activeTab === 'models' ? (
              <section role="tabpanel" id="llm-models-panel" aria-labelledby="llm-models-tab">
                <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex flex-col gap-1">
                    <h2 className="text-lg font-semibold">Model configurations</h2>
                    <p className="text-sm text-muted">Register chat and embedding models after their provider is connected.</p>
                  </div>
                  <button type="button" className="action-button-primary" onClick={handleAddModel}>
                    Add model
                  </button>
                </div>

              {settings.models.length === 0 ? (
                <div className="text-sm text-muted">No model configurations yet.</div>
              ) : (
                <div className="space-y-3">
                  {settings.models.map((model) => (
                    <div key={model.id} className="rounded-lg border border-surface-border bg-surface-alt/60 px-4 py-3">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="font-medium">{modelLabel(model)}</div>
                          <div className="text-sm text-muted">
                            <span>{model.provider}/{model.modelId}</span>
                            <span> · {model.kind}</span>
                            <span> · {model.enabled ? 'enabled' : 'disabled'}</span>
                            {model.kind === 'embedding' && model.dimensions ? <span> · {model.dimensions} dimensions</span> : null}
                            {model.kind === 'chat' && model.temperature != null ? <span> · temperature {model.temperature}</span> : null}
                            {model.kind === 'chat' && model.reasoningEffort ? <span> · reasoning {model.reasoningEffort}</span> : null}
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            className="action-button-secondary"
                            aria-label={`Test ${modelLabel(model)}`}
                            onClick={() => void handleCheckModel(model)}
                            disabled={checkingSavedModelId === model.id}
                          >
                            {checkingSavedModelId === model.id ? 'Testing...' : 'Test'}
                          </button>
                          <button
                            type="button"
                            className="action-button-secondary"
                            aria-label={`Edit ${modelLabel(model)} model settings`}
                            onClick={() => handleEditModel(model)}
                          >
                            Edit
                          </button>
                          <button type="button" className="action-button-secondary" onClick={() => void handleDeleteModel(model)}>
                            Delete
                          </button>
                        </div>
                        {modelCheckResults[model.id] ? (
                          <div className={modelCheckResults[model.id].success ? 'mt-2 text-sm text-accent' : 'mt-2 text-sm text-danger'}>
                            {modelCheckResults[model.id].message}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              </section>
            ) : null}
          </div>
        )}
      </div>

      <ModalCard
        open={activeModal === 'provider'}
        title={editingProvider ? `Edit provider: ${editingProvider}` : 'Create provider'}
        description={editingProvider ? 'Save the provider to apply a new API key.' : 'Connect a provider credential once, then reuse it for model configs.'}
        onOpenChange={(open) => {
          if (!open) closeProviderModal()
        }}
        footer={
          <>
            <button type="button" className="action-button-secondary" onClick={closeProviderModal} disabled={savingProvider}>
              Cancel
            </button>
            <button type="button" className="action-button-secondary" onClick={() => void handleProviderFormCheck()} disabled={checkingProviderForm}>
              {checkingProviderForm ? 'Testing...' : 'Test provider'}
            </button>
            <button type="submit" form="llm-provider-form" className="action-button-primary" disabled={savingProvider}>
              {savingProvider ? 'Saving...' : editingProvider ? 'Save provider' : 'Create provider'}
            </button>
          </>
        }
      >
        <form id="llm-provider-form" className="grid gap-4 md:grid-cols-2" onSubmit={handleProviderSubmit}>
          <label className="field">
              <span className="text-sm font-medium">Provider ID</span>
              <input
                className="field-input"
                value={providerForm.name}
                placeholder="openai"
                disabled={editingProvider !== null}
                onChange={(event) => handleProviderNameChange(event.target.value)}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">API type</span>
              <select
                className="field-input"
                value={providerForm.apiType}
                onChange={(event) => setProviderForm((state) => ({ ...state, apiType: event.target.value as LlmApiType }))}
              >
                {API_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="text-sm font-medium">Base URL</span>
              <input
                className="field-input"
                value={providerForm.baseUrl}
                onChange={(event) => setProviderForm((state) => ({ ...state, baseUrl: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Timeout, seconds</span>
              <input
                className="field-input"
                type="number"
                min={1}
                max={3600}
                value={providerForm.requestTimeoutSeconds}
                onChange={(event) => setProviderForm((state) => ({ ...state, requestTimeoutSeconds: event.target.value }))}
              />
            </label>
            <label className="field md:col-span-2">
              <span className="text-sm font-medium">API key</span>
              <input
                className="field-input"
                type="password"
                autoComplete="new-password"
                autoCorrect="off"
                autoCapitalize="off"
                spellCheck={false}
                data-lpignore="true"
                value={providerForm.apiKey}
                placeholder={editingProvider && settings.providers[editingProvider]?.apiKey?.present ? 'Secret is configured (hidden)' : 'Enter API key'}
                onChange={(event) => setProviderForm((state) => ({ ...state, apiKey: event.target.value }))}
              />
              {editingProvider && settings.providers[editingProvider]?.apiKey?.present ? (
                <span className="text-xs text-muted">Leave blank to keep the current secret.</span>
              ) : null}
            </label>
            {providerFormCheckResult ? (
              <span className={providerFormCheckResult.success ? 'text-sm text-accent md:col-span-2' : 'text-sm text-danger md:col-span-2'}>
                {providerFormCheckResult.message}
              </span>
            ) : null}
        </form>
      </ModalCard>

      <ModalCard
        open={activeModal === 'model'}
        title={editingModelId ? 'Edit model' : 'Create model'}
        description="Configure the provider-backed model that spaces can use."
        onOpenChange={(open) => {
          if (!open) closeModelModal()
        }}
        footer={
          <>
            <button type="button" className="action-button-secondary" onClick={closeModelModal} disabled={savingModel}>
              Cancel
            </button>
            <button type="button" className="action-button-secondary" onClick={() => void handleModelFormCheck()} disabled={checkingModelForm || providerNames.length === 0}>
              {checkingModelForm ? 'Testing...' : 'Test model'}
            </button>
            <button type="submit" form="llm-model-form" className="action-button-primary" disabled={savingModel || providerNames.length === 0}>
              {savingModel ? 'Saving...' : editingModelId ? 'Save model' : 'Create model'}
            </button>
          </>
        }
      >
        <form id="llm-model-form" className="grid gap-4 md:grid-cols-2" onSubmit={handleModelSubmit}>
          <label className="field">
              <span className="text-sm font-medium">Provider</span>
              <select
                className="field-input"
                value={modelForm.provider}
                disabled={providerNames.length === 0}
                onChange={(event) => setModelForm((state) => ({ ...state, provider: event.target.value }))}
              >
                {providerNames.map((name) => (
                  <option key={name} value={name}>{name}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="text-sm font-medium">Kind</span>
              <select
                className="field-input"
                value={modelForm.kind}
                onChange={(event) => handleModelKindChange(event.target.value as LlmModelKind)}
              >
                {MODEL_KINDS.map((kind) => (
                  <option key={kind} value={kind}>{kind}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="text-sm font-medium">Model ID</span>
              <input
                className="field-input"
                value={modelForm.modelId}
                onChange={(event) => setModelForm((state) => ({ ...state, modelId: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Display name</span>
              <input
                className="field-input"
                value={modelForm.displayName}
                placeholder="Optional"
                onChange={(event) => setModelForm((state) => ({ ...state, displayName: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Max input tokens</span>
              <input
                className="field-input"
                type="number"
                min={1}
                value={modelForm.maxInputTokens}
                onChange={(event) => setModelForm((state) => ({ ...state, maxInputTokens: event.target.value }))}
              />
            </label>
            {modelForm.kind === 'embedding' ? (
              <label className="field">
                <span className="text-sm font-medium">Dimensions</span>
                <input
                  className="field-input"
                  type="number"
                  min={1}
                  value={modelForm.dimensions}
                  onChange={(event) => setModelForm((state) => ({ ...state, dimensions: event.target.value }))}
                />
              </label>
            ) : (
              <>
                <label className="field">
                  <span className="text-sm font-medium">Chat tuning</span>
                  <select
                    className="field-input"
                    value={modelForm.chatTuning}
                    onChange={(event) => setModelForm((state) => ({ ...state, chatTuning: event.target.value as 'temperature' | 'reasoning' }))}
                  >
                    <option value="temperature">Temperature</option>
                    <option value="reasoning">Reasoning</option>
                  </select>
                </label>
                {modelForm.chatTuning === 'temperature' ? (
                  <label className="field">
                    <span className="text-sm font-medium">Temperature</span>
                    <input
                      className="field-input"
                      type="number"
                      min={0}
                      max={2}
                      step="0.1"
                      value={modelForm.temperature}
                      onChange={(event) => setModelForm((state) => ({ ...state, temperature: event.target.value }))}
                    />
                  </label>
                ) : (
                  <label className="field">
                    <span className="text-sm font-medium">Reasoning effort</span>
                    <select
                      className="field-input"
                      value={modelForm.reasoningEffort}
                      onChange={(event) => setModelForm((state) => ({ ...state, reasoningEffort: event.target.value as 'low' | 'medium' | 'high' }))}
                    >
                      {REASONING_EFFORTS.map((effort) => (
                        <option key={effort} value={effort}>{effort}</option>
                      ))}
                    </select>
                  </label>
                )}
              </>
            )}
            <label className="flex items-center gap-2 text-sm md:col-span-2">
              <input
                type="checkbox"
                checked={modelForm.enabled}
                onChange={(event) => setModelForm((state) => ({ ...state, enabled: event.target.checked }))}
              />
              Enabled
            </label>
            {modelFormCheckResult ? (
              <span className={modelFormCheckResult.success ? 'text-sm text-accent md:col-span-2' : 'text-sm text-danger md:col-span-2'}>
                {modelFormCheckResult.message}
              </span>
            ) : null}
        </form>
      </ModalCard>
    </div>
  )
}
