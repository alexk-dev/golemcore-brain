import type {
  ApiKey,
  AuthConfig,
  ConvertPagePayload,
  CopyPagePayload,
  CreateApiKeyPayload,
  CreatePagePayload,
  IssuedApiKey,
  LlmProviderCheckResult,
  LlmSettings,
  MovePagePayload,
  PublicUserView,
  MarkdownImportOptions,
  SaveLlmModelPayload,
  SaveLlmProviderPayload,
  Space,
  UpdatePagePayload,
  UpdateUserPayload,
  UserRole,
  WikiAsset,
  WikiConfig,
  WikiImportApplyResponse,
  WikiImportPlanResponse,
  WikiPageHistoryVersion,
  WikiLinkStatus,
  WikiPage,
  WikiPageHistoryEntry,
  WikiPathLookupResult,
  WikiSearchHit,
  WikiSearchStatus,
  WikiTreeNode,
} from '../types'

type ErrorBody = {
  error?: string
  code?: string
  expectedRevision?: string
  currentRevision?: string
  currentPage?: WikiPage
}

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export class PageConflictError extends ApiError {
  expectedRevision?: string
  currentRevision?: string
  currentPage: WikiPage

  constructor(message: string, body: ErrorBody) {
    super(message, 409, body.code)
    this.name = 'PageConflictError'
    this.expectedRevision = body.expectedRevision
    this.currentRevision = body.currentRevision
    this.currentPage = body.currentPage as WikiPage
  }
}

let currentSpaceSlug = 'default'

export function setCurrentSpaceSlug(slug: string) {
  currentSpaceSlug = slug
}

export function getCurrentSpaceSlug(): string {
  return currentSpaceSlug
}

function spaceUrl(suffix: string): string {
  const normalized = suffix.startsWith('/') ? suffix : `/${suffix}`
  return `/api/spaces/${encodeURIComponent(currentSpaceSlug)}${normalized}`
}

async function readJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    credentials: 'include',
    headers: {
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    const body = (await response.json().catch(() => ({ error: 'Request failed' }))) as ErrorBody
    const message = body.error ?? 'Request failed'
    if (response.status === 409 && body.code === 'PAGE_EDIT_CONFLICT' && body.currentPage) {
      throw new PageConflictError(message, body)
    }
    throw new ApiError(message, response.status, body.code)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function getAuthConfig(): Promise<AuthConfig> {
  return readJson<AuthConfig>('/api/auth/config')
}

export function login(identifier: string, password: string): Promise<{ message: string; user: PublicUserView }> {
  return readJson('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ identifier, password }),
  })
}

export function logout(): Promise<{ message: string; user: null }> {
  return readJson('/api/auth/logout', {
    method: 'POST',
  })
}

export function changePassword(currentPassword: string, newPassword: string): Promise<{ message: string; user: null }> {
  return readJson('/api/auth/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export function listUsers(): Promise<PublicUserView[]> {
  return readJson('/api/auth/users')
}

export function createUser(username: string, email: string, password: string, role: UserRole): Promise<PublicUserView> {
  return readJson('/api/auth/users', {
    method: 'POST',
    body: JSON.stringify({ username, email, password, role }),
  })
}

export function updateUser(userId: string, payload: UpdateUserPayload): Promise<PublicUserView> {
  return readJson(`/api/auth/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteUserAccount(userId: string): Promise<void> {
  return readJson(`/api/auth/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  })
}

export function getConfig(): Promise<WikiConfig> {
  return readJson<WikiConfig>('/api/config')
}

export function listSpaces(): Promise<Space[]> {
  return readJson<Space[]>('/api/spaces')
}

export function createSpace(slug: string, name: string): Promise<Space> {
  return readJson<Space>('/api/spaces', {
    method: 'POST',
    body: JSON.stringify({ slug, name }),
  })
}

export function deleteSpace(slug: string): Promise<void> {
  return readJson<void>(`/api/spaces/${encodeURIComponent(slug)}`, {
    method: 'DELETE',
  })
}

export function listGlobalApiKeys(): Promise<ApiKey[]> {
  return readJson<ApiKey[]>('/api/api-keys')
}

export function createGlobalApiKey(payload: CreateApiKeyPayload): Promise<IssuedApiKey> {
  return readJson<IssuedApiKey>('/api/api-keys', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listSpaceApiKeys(slug: string): Promise<ApiKey[]> {
  return readJson<ApiKey[]>(`/api/spaces/${encodeURIComponent(slug)}/api-keys`)
}

export function createSpaceApiKey(slug: string, payload: CreateApiKeyPayload): Promise<IssuedApiKey> {
  return readJson<IssuedApiKey>(`/api/spaces/${encodeURIComponent(slug)}/api-keys`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function revokeApiKey(keyId: string): Promise<void> {
  return readJson<void>(`/api/api-keys/${encodeURIComponent(keyId)}`, {
    method: 'DELETE',
  })
}

export function getLlmSettings(): Promise<LlmSettings> {
  return readJson<LlmSettings>('/api/llm/settings')
}

export function createLlmProvider(payload: SaveLlmProviderPayload): Promise<LlmSettings> {
  return readJson<LlmSettings>('/api/llm/providers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateLlmProvider(name: string, payload: SaveLlmProviderPayload): Promise<LlmSettings> {
  return readJson<LlmSettings>(`/api/llm/providers/${encodeURIComponent(name)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteLlmProvider(name: string): Promise<LlmSettings> {
  return readJson<LlmSettings>(`/api/llm/providers/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  })
}

export function checkLlmProvider(name: string): Promise<LlmProviderCheckResult> {
  return readJson<LlmProviderCheckResult>(`/api/llm/providers/${encodeURIComponent(name)}/check`, {
    method: 'POST',
  })
}

export function createLlmModel(payload: SaveLlmModelPayload): Promise<LlmSettings> {
  return readJson<LlmSettings>('/api/llm/models', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateLlmModel(id: string, payload: SaveLlmModelPayload): Promise<LlmSettings> {
  return readJson<LlmSettings>(`/api/llm/models/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteLlmModel(id: string): Promise<LlmSettings> {
  return readJson<LlmSettings>(`/api/llm/models/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

export function getTree(): Promise<WikiTreeNode> {
  return readJson<WikiTreeNode>(spaceUrl('/tree'))
}

export function getPage(path: string): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page?path=${encodeURIComponent(path)}`))
}

export function getPageByPath(path: string): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/pages/by-path?path=${encodeURIComponent(path)}`))
}

export function getPageHistory(path: string): Promise<WikiPageHistoryEntry[]> {
  return readJson<WikiPageHistoryEntry[]>(spaceUrl(`/page/history?path=${encodeURIComponent(path)}`))
}

export function getPageHistoryVersion(path: string, versionId: string): Promise<WikiPageHistoryVersion> {
  return readJson<WikiPageHistoryVersion>(spaceUrl(`/page/history/version?path=${encodeURIComponent(path)}&versionId=${encodeURIComponent(versionId)}`))
}

export function restorePageHistory(path: string, versionId: string): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page/history/restore?path=${encodeURIComponent(path)}&versionId=${encodeURIComponent(versionId)}`), {
    method: 'POST',
  })
}

export function searchPages(query: string): Promise<WikiSearchHit[]> {
  return readJson<WikiSearchHit[]>(spaceUrl(`/search?q=${encodeURIComponent(query)}`))
}

export function getSearchStatus(): Promise<WikiSearchStatus> {
  return readJson<WikiSearchStatus>(spaceUrl('/search/status'))
}

function appendImportOptions(formData: FormData, options?: MarkdownImportOptions) {
  if (!options) {
    return
  }
  formData.append(
    'options',
    new Blob([JSON.stringify(options)], { type: 'application/json' }),
  )
}

export function planMarkdownImport(file: File, options?: MarkdownImportOptions): Promise<WikiImportPlanResponse> {
  const formData = new FormData()
  formData.append('file', file)
  appendImportOptions(formData, options)
  return readJson<WikiImportPlanResponse>(spaceUrl('/import/markdown/plan'), {
    method: 'POST',
    body: formData,
  })
}

export function applyMarkdownImport(file: File, options?: MarkdownImportOptions): Promise<WikiImportApplyResponse> {
  const formData = new FormData()
  formData.append('file', file)
  appendImportOptions(formData, options)
  return readJson<WikiImportApplyResponse>(spaceUrl('/import/markdown/apply'), {
    method: 'POST',
    body: formData,
  })
}

export function createPage(payload: CreatePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl('/pages'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function ensurePage(path: string, targetTitle?: string): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl('/pages/ensure'), {
    method: 'POST',
    body: JSON.stringify({ path, targetTitle }),
  })
}

export function lookupPath(path: string): Promise<WikiPathLookupResult> {
  return readJson<WikiPathLookupResult>(spaceUrl(`/pages/lookup?path=${encodeURIComponent(path)}`))
}

export function updatePage(path: string, payload: UpdatePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page?path=${encodeURIComponent(path)}`), {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deletePage(path: string): Promise<void> {
  return readJson<void>(spaceUrl(`/page?path=${encodeURIComponent(path)}`), {
    method: 'DELETE',
  })
}

export function movePage(path: string, payload: MovePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page/move?path=${encodeURIComponent(path)}`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function copyPage(path: string, payload: CopyPagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page/copy?path=${encodeURIComponent(path)}`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function convertPage(path: string, payload: ConvertPagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(spaceUrl(`/page/convert?path=${encodeURIComponent(path)}`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function sortSection(path: string, orderedSlugs: string[]): Promise<void> {
  return readJson<void>(spaceUrl(`/section/sort?path=${encodeURIComponent(path)}`), {
    method: 'PUT',
    body: JSON.stringify({ orderedSlugs }),
  })
}

export function getLinkStatus(path: string): Promise<WikiLinkStatus> {
  return readJson<WikiLinkStatus>(spaceUrl(`/links?path=${encodeURIComponent(path)}`))
}

export function listAssets(path: string): Promise<WikiAsset[]> {
  return readJson<WikiAsset[]>(spaceUrl(`/pages/assets?path=${encodeURIComponent(path)}`))
}

export function uploadAsset(path: string, file: File): Promise<WikiAsset> {
  const formData = new FormData()
  formData.append('file', file)
  return readJson<WikiAsset>(spaceUrl(`/pages/assets?path=${encodeURIComponent(path)}`), {
    method: 'POST',
    body: formData,
  })
}

export function renameAsset(path: string, oldName: string, newName: string): Promise<WikiAsset> {
  return readJson<WikiAsset>(spaceUrl(`/pages/assets/rename?path=${encodeURIComponent(path)}`), {
    method: 'PUT',
    body: JSON.stringify({ oldName, newName }),
  })
}

export function deleteAsset(path: string, name: string): Promise<void> {
  return readJson<void>(spaceUrl(`/pages/assets?path=${encodeURIComponent(path)}&name=${encodeURIComponent(name)}`), {
    method: 'DELETE',
  })
}
