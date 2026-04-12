import { describe, expect, it } from 'vitest'

import { setCurrentSpaceSlug } from '../../lib/api'
import type { WikiAsset } from '../../types'
import { buildDefaultMarkdownForAsset, buildImageMarkdown, buildLinkMarkdown, buildMediaMarkdown } from './assetMarkdown'

const imageAsset: WikiAsset = {
  name: 'image.png',
  path: '/api/assets?path=docs/page&name=image.png',
  size: 1024,
  contentType: 'image/png',
}

const audioAsset: WikiAsset = {
  name: 'audio.mp3',
  path: '/api/assets?path=docs/page&name=audio.mp3',
  size: 2048,
  contentType: 'audio/mpeg',
}

describe('assetMarkdown', () => {
  it('builds markdown variants with space-scoped asset URLs', () => {
    setCurrentSpaceSlug('default')

    expect(buildImageMarkdown(imageAsset)).toBe('![image.png](/api/spaces/default/assets?path=docs/page&name=image.png)')
    expect(buildLinkMarkdown(imageAsset)).toBe('[image.png](/api/spaces/default/assets?path=docs/page&name=image.png)')
    expect(buildMediaMarkdown(audioAsset)).toBe('<audio controls src="/api/spaces/default/assets?path=docs/page&name=audio.mp3"></audio>')
    expect(buildDefaultMarkdownForAsset(audioAsset)).toBe('<audio controls src="/api/spaces/default/assets?path=docs/page&name=audio.mp3"></audio>')
  })
})
