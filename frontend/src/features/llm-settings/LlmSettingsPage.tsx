import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'

import {
  checkLlmProvider,
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
}

const EMPTY_SETTINGS: LlmSettings = { providers: {}, models: [] }
const API_TYPES: LlmApiType[] = ['openai', 'anthropic', 'gemini']
const MODEL_KINDS: LlmModelKind[] = ['chat', 'embedding']

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
    name: '',
    apiType: 'openai',
    baseUrl: '',
    requestTimeoutSeconds: '300',
    apiKey: '',
  }
}

function emptyModelForm(provider = ''): ModelFormState {
  return {
    provider,
    kind: 'chat',
    modelId: '',
    displayName: '',
    enabled: true,
    maxInputTokens: '',
    dimensions: '',
    temperature: '',
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
  const [checkResults, setCheckResults] = useState<Record<string, LlmProviderCheckResult>>({})

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
    const secret = toSecret(providerForm.apiKey)
    const payload: SaveLlmProviderPayload = {
      ...(editingProvider ? {} : { name }),
      ...(secret ? { apiKey: secret } : {}),
      apiType: providerForm.apiType,
      baseUrl: toNullableString(providerForm.baseUrl),
      requestTimeoutSeconds: toNullableNumber(providerForm.requestTimeoutSeconds),
    }
    setSavingProvider(true)
    try {
      const response = editingProvider
        ? await updateLlmProvider(editingProvider, payload)
        : await createLlmProvider(payload)
      setSettings(response)
      toast.success(editingProvider ? 'Provider updated' : 'Provider created')
      resetProviderForm()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSavingProvider(false)
    }
  }

  const handleEditProvider = (name: string) => {
    const provider = settings.providers[name]
    if (!provider) return
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
    const payload: SaveLlmModelPayload = {
      provider: modelForm.provider,
      modelId: modelForm.modelId.trim(),
      displayName: toNullableString(modelForm.displayName),
      kind: modelForm.kind,
      enabled: modelForm.enabled,
      maxInputTokens: toNullableNumber(modelForm.maxInputTokens),
      dimensions: modelForm.kind === 'embedding' ? toNullableNumber(modelForm.dimensions) : null,
      temperature: modelForm.kind === 'chat' ? toNullableNumber(modelForm.temperature) : null,
    }
    setSavingModel(true)
    try {
      const response = editingModelId
        ? await updateLlmModel(editingModelId, payload)
        : await createLlmModel(payload)
      setSettings(response)
      toast.success(editingModelId ? 'Model updated' : 'Model created')
      resetModelForm()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSavingModel(false)
    }
  }

  const handleEditModel = (model: LlmModelConfig) => {
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
    })
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

            <section>
              <h2 className="mb-3 text-lg font-semibold">Providers</h2>
              <form className="mb-5 grid gap-4 border-b border-surface-border pb-5 md:grid-cols-2" onSubmit={handleProviderSubmit}>
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
                    placeholder="https://api.openai.com/v1"
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
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <button type="submit" className="action-button-primary" disabled={savingProvider}>
                    {savingProvider ? 'Saving...' : editingProvider ? 'Save provider' : 'Create provider'}
                  </button>
                  {editingProvider ? (
                    <button type="button" className="action-button-secondary" onClick={resetProviderForm} disabled={savingProvider}>
                      Cancel
                    </button>
                  ) : null}
                </div>
              </form>

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
                            <button type="button" className="action-button-secondary" onClick={() => void handleCheckProvider(name)} disabled={checkingProvider === name}>
                              {checkingProvider === name ? 'Checking...' : 'Check'}
                            </button>
                            <button type="button" className="action-button-secondary" onClick={() => handleEditProvider(name)}>
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

            <section>
              <h2 className="mb-3 text-lg font-semibold">Model configurations</h2>
              <form className="mb-5 grid gap-4 border-b border-surface-border pb-5 md:grid-cols-2" onSubmit={handleModelSubmit}>
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
                    onChange={(event) => setModelForm((state) => ({ ...state, kind: event.target.value as LlmModelKind }))}
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
                    placeholder={modelForm.kind === 'embedding' ? 'text-embedding-3-large' : 'gpt-5.4'}
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
                )}
                <label className="flex items-center gap-2 text-sm md:col-span-2">
                  <input
                    type="checkbox"
                    checked={modelForm.enabled}
                    onChange={(event) => setModelForm((state) => ({ ...state, enabled: event.target.checked }))}
                  />
                  Enabled
                </label>
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <button type="submit" className="action-button-primary" disabled={savingModel || providerNames.length === 0}>
                    {savingModel ? 'Saving...' : editingModelId ? 'Save model' : 'Create model'}
                  </button>
                  {editingModelId ? (
                    <button type="button" className="action-button-secondary" onClick={resetModelForm} disabled={savingModel}>
                      Cancel
                    </button>
                  ) : null}
                </div>
              </form>

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
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <button type="button" className="action-button-secondary" onClick={() => handleEditModel(model)}>
                            Edit
                          </button>
                          <button type="button" className="action-button-secondary" onClick={() => void handleDeleteModel(model)}>
                            Delete
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </div>
    </div>
  )
}
