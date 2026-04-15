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

import * as api from '../../lib/api'
import { isAppApiPath, withAppBasePath } from '../../lib/basePath'
import type { WikiAsset } from '../../types'

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z0-9+.-]*:/i.test(url) || url.startsWith('//')
}

function buildAssetUrl(suffix: string): string {
  const assetUrl = api.assetUrl
  if (typeof assetUrl === 'function') {
    return assetUrl(suffix)
  }
  return `/api/spaces/default${suffix}`
}

export function normalizeAssetUrl(url: string): string {
  if (isAbsoluteUrl(url) || !url.startsWith('/api/assets')) {
    return url
  }
  return buildAssetUrl(url.slice('/api'.length))
}

export function toBrowserAssetUrl(url: string): string {
  const normalizedUrl = normalizeAssetUrl(url)
  return isAppApiPath(normalizedUrl) ? withAppBasePath(normalizedUrl) : normalizedUrl
}

export function normalizeAsset(asset: WikiAsset): WikiAsset {
  const normalizedPath = normalizeAssetUrl(asset.path)
  if (normalizedPath === asset.path) {
    return asset
  }
  return {
    ...asset,
    path: normalizedPath,
  }
}
