import type { WikiAsset } from '../../types'

export function buildImageMarkdown(asset: WikiAsset): string {
  return `![${asset.name}](${asset.path})`
}

export function buildLinkMarkdown(asset: WikiAsset): string {
  return `[${asset.name}](${asset.path})`
}

export function buildMediaMarkdown(asset: WikiAsset): string {
  if (asset.contentType.startsWith('audio/')) {
    return `<audio controls src="${asset.path}"></audio>`
  }
  if (asset.contentType.startsWith('video/')) {
    return `<video controls src="${asset.path}"></video>`
  }
  return buildLinkMarkdown(asset)
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
