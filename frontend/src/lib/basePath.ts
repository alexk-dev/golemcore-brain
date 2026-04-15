/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

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
