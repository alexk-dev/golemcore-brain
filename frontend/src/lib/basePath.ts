function isAbsoluteUrl(value: string): boolean {
  return /^[a-z][a-z0-9+.-]*:/i.test(value) || value.startsWith('//')
}

export function normalizeAppBasePath(path: string): string {
  const normalized = path.trim().replace(/\/+$/, '')
  if (!normalized || normalized === '/') {
    return ''
  }
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

export function detectAppBasePathFromBaseUri(baseUri: string): string {
  if (typeof window === 'undefined') {
    return ''
  }
  try {
    return normalizeAppBasePath(new URL(baseUri, window.location.href).pathname)
  } catch {
    return ''
  }
}

export const appBasePath = typeof document === 'undefined' ? '' : detectAppBasePathFromBaseUri(document.baseURI)

export function withAppBasePath(path: string, basePath = appBasePath): string {
  const normalizedBasePath = normalizeAppBasePath(basePath)
  if (!normalizedBasePath || isAbsoluteUrl(path) || !path.startsWith('/')) {
    return path
  }
  if (path === normalizedBasePath || path.startsWith(`${normalizedBasePath}/`)) {
    return path
  }
  return `${normalizedBasePath}${path}`
}

export function stripAppBasePath(path: string, basePath = appBasePath): string {
  const normalizedBasePath = normalizeAppBasePath(basePath)
  if (!normalizedBasePath || isAbsoluteUrl(path) || !path.startsWith('/')) {
    return path
  }
  if (path === normalizedBasePath) {
    return '/'
  }
  if (path.startsWith(`${normalizedBasePath}/`)) {
    return path.slice(normalizedBasePath.length)
  }
  return path
}

export function isAppApiPath(path: string, basePath = appBasePath): boolean {
  return stripAppBasePath(path, basePath).startsWith('/api/')
}
