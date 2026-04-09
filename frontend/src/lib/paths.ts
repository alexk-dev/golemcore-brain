export function normalizeWikiPath(path: string): string {
  return path.replace(/^\/+/, '').replace(/\/+$/, '')
}

export function pathToRoute(path: string): string {
  const normalized = normalizeWikiPath(path)
  return normalized ? `/${normalized}` : '/'
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
