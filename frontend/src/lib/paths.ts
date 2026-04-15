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
