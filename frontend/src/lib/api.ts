import type {
  AuthConfig,
  CopyPagePayload,
  CreatePagePayload,
  MovePagePayload,
  PublicUserView,
  UpdatePagePayload,
  UpdateUserPayload,
  UserRole,
  WikiAsset,
  WikiConfig,
  WikiImportApplyResponse,
  WikiImportPlanResponse,
  WikiLinkStatus,
  WikiPage,
  WikiPageHistoryEntry,
  WikiPathLookupResult,
  WikiSearchHit,
  WikiSearchStatus,
  WikiTreeNode,
} from '../types'

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
    const body = (await response.json().catch(() => ({ error: 'Request failed' }))) as {
      error?: string
    }
    throw new Error(body.error ?? 'Request failed')
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

export function getTree(): Promise<WikiTreeNode> {
  return readJson<WikiTreeNode>('/api/tree')
}

export function getPage(path: string): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page?path=${encodeURIComponent(path)}`)
}

export function getPageByPath(path: string): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/pages/by-path?path=${encodeURIComponent(path)}`)
}

export function getPageHistory(path: string): Promise<WikiPageHistoryEntry[]> {
  return readJson<WikiPageHistoryEntry[]>(`/api/page/history?path=${encodeURIComponent(path)}`)
}

export function restorePageHistory(path: string, versionId: string): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page/history/restore?path=${encodeURIComponent(path)}&versionId=${encodeURIComponent(versionId)}`, {
    method: 'POST',
  })
}

export function searchPages(query: string): Promise<WikiSearchHit[]> {
  return readJson<WikiSearchHit[]>(`/api/search?q=${encodeURIComponent(query)}`)
}

export function getSearchStatus(): Promise<WikiSearchStatus> {
  return readJson<WikiSearchStatus>('/api/search/status')
}

export function planMarkdownImport(file: File): Promise<WikiImportPlanResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return readJson<WikiImportPlanResponse>('/api/import/markdown/plan', {
    method: 'POST',
    body: formData,
  })
}

export function applyMarkdownImport(file: File): Promise<WikiImportApplyResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return readJson<WikiImportApplyResponse>('/api/import/markdown/apply', {
    method: 'POST',
    body: formData,
  })
}

export function createPage(payload: CreatePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>('/api/pages', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function ensurePage(path: string, targetTitle?: string): Promise<WikiPage> {
  return readJson<WikiPage>('/api/pages/ensure', {
    method: 'POST',
    body: JSON.stringify({ path, targetTitle }),
  })
}

export function lookupPath(path: string): Promise<WikiPathLookupResult> {
  return readJson<WikiPathLookupResult>(`/api/pages/lookup?path=${encodeURIComponent(path)}`)
}

export function updatePage(path: string, payload: UpdatePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page?path=${encodeURIComponent(path)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deletePage(path: string): Promise<void> {
  return readJson<void>(`/api/page?path=${encodeURIComponent(path)}`, {
    method: 'DELETE',
  })
}

export function movePage(path: string, payload: MovePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page/move?path=${encodeURIComponent(path)}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function copyPage(path: string, payload: CopyPagePayload): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page/copy?path=${encodeURIComponent(path)}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function sortSection(path: string, orderedSlugs: string[]): Promise<void> {
  return readJson<void>(`/api/section/sort?path=${encodeURIComponent(path)}`, {
    method: 'PUT',
    body: JSON.stringify({ orderedSlugs }),
  })
}

export function getLinkStatus(path: string): Promise<WikiLinkStatus> {
  return readJson<WikiLinkStatus>(`/api/links?path=${encodeURIComponent(path)}`)
}

export function listAssets(path: string): Promise<WikiAsset[]> {
  return readJson<WikiAsset[]>(`/api/pages/assets?path=${encodeURIComponent(path)}`)
}

export function uploadAsset(path: string, file: File): Promise<WikiAsset> {
  const formData = new FormData()
  formData.append('file', file)
  return readJson<WikiAsset>(`/api/pages/assets?path=${encodeURIComponent(path)}`, {
    method: 'POST',
    body: formData,
  })
}

export function renameAsset(path: string, oldName: string, newName: string): Promise<WikiAsset> {
  return readJson<WikiAsset>(`/api/pages/assets/rename?path=${encodeURIComponent(path)}`, {
    method: 'PUT',
    body: JSON.stringify({ oldName, newName }),
  })
}

export function deleteAsset(path: string, name: string): Promise<void> {
  return readJson<void>(`/api/pages/assets?path=${encodeURIComponent(path)}&name=${encodeURIComponent(name)}`, {
    method: 'DELETE',
  })
}
