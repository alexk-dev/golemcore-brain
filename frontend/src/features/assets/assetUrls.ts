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
