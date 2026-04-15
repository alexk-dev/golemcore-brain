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
