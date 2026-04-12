import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import {
  createDynamicSpaceApi,
  deleteDynamicSpaceApi,
  getLlmSettings,
  listDynamicSpaceApis,
  runDynamicSpaceApi,
  updateDynamicSpaceApi,
} from '../../lib/api'
import { useSpaceStore } from '../../stores/space'
import { useUiStore } from '../../stores/ui'
import type {
  DynamicSpaceApiConfig,
  DynamicSpaceApiRunResult,
  LlmModelConfig,
  SaveDynamicSpaceApiPayload,
} from '../../types'
import { DynamicApiEndpointPreview } from './DynamicApiEndpointPreview'
import { buildDynamicApiRunPath } from './dynamicApiUrls'

interface ApiFormState {
  slug: string
  name: string
  description: string
  modelConfigId: string
  systemPrompt: string
  enabled: boolean
  maxIterations: string
}

const DEFAULT_PROMPT = `You answer search requests for this wiki space.
Use filesystem tools to find and read relevant markdown files before answering.
Return a JSON object with answer, sources, and confidence fields.`

function emptyForm(modelConfigId = ''): ApiFormState {
  return {
    slug: '',
    name: '',
    description: '',
    modelConfigId,
    systemPrompt: DEFAULT_PROMPT,
    enabled: true,
    maxIterations: '6',
  }
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

function normalizeSlug(value: string): string {
  return value.trim().toLowerCase().replaceAll(/\s+/g, '-')
}

function modelLabel(model: LlmModelConfig): string {
  return model.displayName?.trim() || `${model.provider}/${model.modelId}`
}

interface DynamicSpaceApisPageProps {
  spaceSlug?: string
  embedded?: boolean
}

export function DynamicSpaceApisPage({ spaceSlug, embedded = false }: DynamicSpaceApisPageProps) {
  const currentUser = useUiStore((state) => state.currentUser)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const isAdmin = authDisabled || currentUser?.role === 'ADMIN'
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const targetSpaceSlug = spaceSlug ?? activeSlug

  const [apis, setApis] = useState<DynamicSpaceApiConfig[]>([])
  const [chatModels, setChatModels] = useState<LlmModelConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<ApiFormState>(emptyForm())
  const [testApiSlug, setTestApiSlug] = useState('')
  const [testPayload, setTestPayload] = useState('{\n  "query": "What should I know from this space?"\n}')
  const [testResult, setTestResult] = useState<DynamicSpaceApiRunResult | null>(null)
  const [running, setRunning] = useState(false)

  const firstModelId = chatModels[0]?.id ?? ''
  const modelById = useMemo(() => new Map(chatModels.map((model) => [model.id, model])), [chatModels])

  const loadData = useCallback(async () => {
    if (!isAdmin) return
    setLoading(true)
    try {
      const [apiList, llmSettings] = await Promise.all([listDynamicSpaceApis(targetSpaceSlug), getLlmSettings()])
      const models = llmSettings.models.filter((model) => model.kind === 'chat' && model.enabled)
      setApis(apiList)
      setChatModels(models)
      setForm((state) => ({
        ...state,
        modelConfigId: state.modelConfigId || models[0]?.id || '',
      }))
      setTestApiSlug((current) => (apiList.some((api) => api.slug === current) ? current : apiList[0]?.slug || ''))
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setLoading(false)
    }
  }, [isAdmin, targetSpaceSlug])

  useEffect(() => {
    void loadData()
  }, [targetSpaceSlug, loadData])

  if (!isAdmin) {
    return (
      <div className="shell-form-page">
        <div className="shell-form-page__card--wide surface-card p-6">
          <h2 className="mb-2 text-xl font-semibold">Admin access required</h2>
          <p className="text-sm text-muted">You must be signed in as an administrator to manage dynamic APIs.</p>
        </div>
      </div>
    )
  }

  const resetForm = () => {
    setEditingId(null)
    setForm(emptyForm(firstModelId))
  }

  const handleEdit = (api: DynamicSpaceApiConfig) => {
    setEditingId(api.id)
    setForm({
      slug: api.slug,
      name: api.name,
      description: api.description ?? '',
      modelConfigId: api.modelConfigId,
      systemPrompt: api.systemPrompt,
      enabled: api.enabled,
      maxIterations: String(api.maxIterations ?? 6),
    })
  }

  const handleDelete = async (api: DynamicSpaceApiConfig) => {
    if (!window.confirm(`Delete dynamic API ${api.slug}?`)) return
    try {
      await deleteDynamicSpaceApi(api.id, targetSpaceSlug)
      toast.success('Dynamic API deleted')
      if (editingId === api.id) resetForm()
      await loadData()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (chatModels.length === 0) {
      toast.error('Create an enabled chat model first')
      return
    }
    const payload: SaveDynamicSpaceApiPayload = {
      slug: normalizeSlug(form.slug),
      name: toNullableString(form.name),
      description: toNullableString(form.description),
      modelConfigId: form.modelConfigId,
      systemPrompt: form.systemPrompt.trim(),
      enabled: form.enabled,
      maxIterations: toNullableNumber(form.maxIterations),
    }
    setSaving(true)
    try {
      const saved = editingId
        ? await updateDynamicSpaceApi(editingId, payload, targetSpaceSlug)
        : await createDynamicSpaceApi(payload, targetSpaceSlug)
      toast.success(editingId ? 'Dynamic API updated' : 'Dynamic API created')
      setTestApiSlug(saved.slug)
      resetForm()
      await loadData()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSaving(false)
    }
  }

  const handleRun = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!testApiSlug) {
      toast.error('Select a dynamic API')
      return
    }
    let payload: Record<string, unknown>
    try {
      payload = JSON.parse(testPayload) as Record<string, unknown>
    } catch {
      toast.error('Request payload must be valid JSON')
      return
    }
    setRunning(true)
    setTestResult(null)
    try {
      setTestResult(await runDynamicSpaceApi(testApiSlug, payload, targetSpaceSlug))
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setRunning(false)
    }
  }

  const content = (
    <div className="surface-card p-6">
        <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="mb-1 text-2xl font-semibold">Dynamic APIs for {targetSpaceSlug}</h1>
            <p className="text-sm text-muted">
              Space-scoped JSON endpoints backed by a configured chat model and filesystem search tools.
            </p>
          </div>
          <Link to="/spaces" className="action-button-secondary">
            All spaces
          </Link>
        </div>

        {loading ? (
          <div className="text-sm text-muted">Loading dynamic APIs...</div>
        ) : (
          <div className="space-y-8">
            <section>
              <h2 className="mb-3 text-lg font-semibold">{editingId ? 'Edit API' : 'Create API'}</h2>
              <form className="mb-5 grid gap-4 border-b border-surface-border pb-5 md:grid-cols-2" onSubmit={handleSubmit}>
                <label className="field">
                  <span className="text-sm font-medium">Slug</span>
                  <input
                    className="field-input"
                    value={form.slug}
                    placeholder="knowledge-search"
                    onChange={(event) => setForm((state) => ({ ...state, slug: normalizeSlug(event.target.value) }))}
                    required
                  />
                </label>
                <label className="field">
                  <span className="text-sm font-medium">Name</span>
                  <input
                    className="field-input"
                    value={form.name}
                    placeholder="Knowledge Search"
                    onChange={(event) => setForm((state) => ({ ...state, name: event.target.value }))}
                  />
                </label>
                <label className="field md:col-span-2">
                  <span className="text-sm font-medium">Description</span>
                  <input
                    className="field-input"
                    value={form.description}
                    placeholder="Answers questions from this space"
                    onChange={(event) => setForm((state) => ({ ...state, description: event.target.value }))}
                  />
                </label>
                <label className="field">
                  <span className="text-sm font-medium">Chat model</span>
                  <select
                    className="field-input"
                    value={form.modelConfigId}
                    disabled={chatModels.length === 0}
                    onChange={(event) => setForm((state) => ({ ...state, modelConfigId: event.target.value }))}
                    required
                  >
                    {chatModels.length === 0 ? <option value="">No enabled chat models</option> : null}
                    {chatModels.map((model) => (
                      <option key={model.id} value={model.id}>
                        {modelLabel(model)}
                      </option>
                    ))}
                  </select>
                </label>
                <details className="rounded-lg border border-surface-border bg-surface-alt/60 px-3 py-2 md:col-span-2">
                  <summary className="cursor-pointer text-sm font-medium">Advanced options</summary>
                  <label className="field mt-3">
                    <span className="text-sm font-medium">Max tool loops</span>
                    <input
                      className="field-input"
                      type="number"
                      min={1}
                      max={20}
                      value={form.maxIterations}
                      onChange={(event) => setForm((state) => ({ ...state, maxIterations: event.target.value }))}
                    />
                  </label>
                </details>
                <DynamicApiEndpointPreview spaceSlug={targetSpaceSlug} apiSlug={form.slug || 'knowledge-search'} />
                <label className="field md:col-span-2">
                  <span className="text-sm font-medium">Instructions / system prompt</span>
                  <textarea
                    className="field-input min-h-56 font-mono text-sm"
                    value={form.systemPrompt}
                    onChange={(event) => setForm((state) => ({ ...state, systemPrompt: event.target.value }))}
                    required
                  />
                </label>
                <label className="flex items-center gap-2 text-sm md:col-span-2">
                  <input
                    type="checkbox"
                    checked={form.enabled}
                    onChange={(event) => setForm((state) => ({ ...state, enabled: event.target.checked }))}
                  />
                  Enabled
                </label>
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <button type="submit" className="action-button-primary" disabled={saving || chatModels.length === 0}>
                    {saving ? 'Saving...' : editingId ? 'Save API' : 'Create API'}
                  </button>
                  {editingId ? (
                    <button type="button" className="action-button-secondary" onClick={resetForm} disabled={saving}>
                      Cancel
                    </button>
                  ) : null}
                </div>
              </form>

              {apis.length === 0 ? (
                <div className="text-sm text-muted">No dynamic APIs in this space.</div>
              ) : (
                <div className="space-y-3">
                  {apis.map((api) => {
                    const model = modelById.get(api.modelConfigId)
                    return (
                      <div key={api.id} className="rounded-lg border border-surface-border bg-surface-alt/60 px-4 py-3">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="font-medium">{api.name || api.slug}</div>
                            <div className="text-sm text-muted">
                              <span>{buildDynamicApiRunPath(targetSpaceSlug, api.slug)}</span>
                              <span> · {model ? modelLabel(model) : 'missing model'}</span>
                              <span> · {api.enabled ? 'enabled' : 'disabled'}</span>
                            </div>
                            {api.description ? <div className="mt-1 text-sm text-muted">{api.description}</div> : null}
                            <div className="mt-2">
                              <DynamicApiEndpointPreview spaceSlug={targetSpaceSlug} apiSlug={api.slug} showCurl />
                            </div>
                          </div>
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className="action-button-secondary" onClick={() => handleEdit(api)}>
                              Edit
                            </button>
                            <button type="button" className="action-button-secondary" onClick={() => void handleDelete(api)}>
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
              <h2 className="mb-3 text-lg font-semibold">Run API</h2>
              <form className="grid gap-4 md:grid-cols-2" onSubmit={handleRun}>
                <label className="field">
                  <span className="text-sm font-medium">API</span>
                  <select
                    className="field-input"
                    value={testApiSlug}
                    disabled={apis.length === 0}
                    onChange={(event) => setTestApiSlug(event.target.value)}
                  >
                    {apis.map((api) => (
                      <option key={api.id} value={api.slug}>
                        {api.name || api.slug}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="hidden md:block" />
                {testApiSlug ? (
                  <DynamicApiEndpointPreview
                    spaceSlug={targetSpaceSlug}
                    apiSlug={testApiSlug}
                    requestJson={testPayload}
                    showCurl
                  />
                ) : null}
                <label className="field md:col-span-2">
                  <span className="text-sm font-medium">Request JSON</span>
                  <textarea
                    className="field-input min-h-40 font-mono text-sm"
                    value={testPayload}
                    onChange={(event) => setTestPayload(event.target.value)}
                  />
                </label>
                <div className="md:col-span-2">
                  <button type="submit" className="action-button-primary" disabled={running || apis.length === 0}>
                    {running ? 'Running...' : 'Run'}
                  </button>
                </div>
              </form>
              {testResult ? (
                <pre className="mt-4 overflow-auto rounded-lg border border-surface-border bg-surface-alt/60 p-4 text-xs">
                  {JSON.stringify(testResult.result, null, 2)}
                </pre>
              ) : null}
            </section>
          </div>
        )}
    </div>
  )

  return embedded ? content : <div className="page-viewer">{content}</div>
}

