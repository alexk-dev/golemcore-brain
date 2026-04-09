export function normalizeWikiPath(path: string): string {
  return path.replace(/^\/+/, '').replace(/\/+$/, '')
}

export function normalizeWikiRoutePath(path: string): string {
  const stripped = path.split('?')[0].split('#')[0]
  if (!stripped || stripped === '/') {
    return '/'
  }
  return `/${normalizeWikiPath(stripped)}`
}

export function pathToRoute(path: string): string {
  const normalized = normalizeWikiPath(path)
  return normalized ? `/${normalized}` : '/'
}

export function editorPathToRoute(path: string): string {
  const normalized = normalizeWikiPath(path)
  return normalized ? `/e/${normalized}` : '/e/'
}

export function parentPath(path: string): string {
  const normalized = normalizeWikiPath(path)
  if (!normalized.includes('/')) {
    return ''
  }
  return normalized.slice(0, normalized.lastIndexOf('/'))
}

export function inferSlug(title: string): string {
  return title
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+/, '')
    .replace(/-+$/, '')
}

export function resolveWikiLinkPath(currentPath: string, href: string): string {
  if (href.startsWith('/')) {
    return normalizeWikiPath(href)
  }
  const normalizedCurrentPath = normalizeWikiPath(currentPath)
  const base = normalizedCurrentPath.includes('/')
    ? normalizedCurrentPath.slice(0, normalizedCurrentPath.lastIndexOf('/'))
    : ''
  const targetPath = new URL(href, `https://brain.local/${base ? `${base}/` : ''}`).pathname
  return normalizeWikiPath(targetPath)
}

export function getParentWikiRoutePath(path: string): string {
  const normalized = normalizeWikiPath(path)
  if (!normalized.includes('/')) {
    return '/'
  }
  return pathToRoute(parentPath(normalized))
}
