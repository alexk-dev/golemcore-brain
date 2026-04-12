import type { WikiAsset } from '../../types'
import { normalizeAsset } from './assetUrls'

export function buildImageMarkdown(asset: WikiAsset): string {
  const normalizedAsset = normalizeAsset(asset)
  return `![${normalizedAsset.name}](${normalizedAsset.path})`
}

export function buildLinkMarkdown(asset: WikiAsset): string {
  const normalizedAsset = normalizeAsset(asset)
  return `[${normalizedAsset.name}](${normalizedAsset.path})`
}

export function buildMediaMarkdown(asset: WikiAsset): string {
  const normalizedAsset = normalizeAsset(asset)
  if (normalizedAsset.contentType.startsWith('audio/')) {
    return `<audio controls src="${normalizedAsset.path}"></audio>`
  }
  if (normalizedAsset.contentType.startsWith('video/')) {
    return `<video controls src="${normalizedAsset.path}"></video>`
  }
  return buildLinkMarkdown(normalizedAsset)
}

export function buildDefaultMarkdownForAsset(asset: WikiAsset): string {
  if (asset.contentType.startsWith('image/')) {
    return buildImageMarkdown(asset)
  }
  if (asset.contentType.startsWith('audio/') || asset.contentType.startsWith('video/')) {
    return buildMediaMarkdown(asset)
  }
  return buildLinkMarkdown(asset)
}
