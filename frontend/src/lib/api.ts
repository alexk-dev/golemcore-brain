import type {
  CopyPagePayload,
  CreatePagePayload,
  MovePagePayload,
  UpdatePagePayload,
  WikiConfig,
  WikiPage,
  WikiSearchHit,
  WikiTreeNode,
} from '../types'

async function readJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    headers: {
      'Content-Type': 'application/json',
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

export function getConfig(): Promise<WikiConfig> {
  return readJson<WikiConfig>('/api/config')
}

export function getTree(): Promise<WikiTreeNode> {
  return readJson<WikiTreeNode>('/api/tree')
}

export function getPage(path: string): Promise<WikiPage> {
  return readJson<WikiPage>(`/api/page?path=${encodeURIComponent(path)}`)
}

export function searchPages(query: string): Promise<WikiSearchHit[]> {
  return readJson<WikiSearchHit[]>(`/api/search?q=${encodeURIComponent(query)}`)
}

export function createPage(payload: CreatePagePayload): Promise<WikiPage> {
  return readJson<WikiPage>('/api/pages', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
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
