import { describe, expect, it } from 'vitest'

import { buildBrowserDynamicApiRunPath, buildDynamicApiRunPath } from './dynamicApiUrls'

describe('dynamic API URLs', () => {
  it('builds a space-scoped run endpoint path', () => {
    expect(buildDynamicApiRunPath('docs', 'knowledge-search')).toBe(
      '/api/spaces/docs/dynamic-apis/knowledge-search/run',
    )
  })

  it('adds the app base path for browser-visible run endpoint paths', () => {
    expect(buildBrowserDynamicApiRunPath('docs', 'knowledge search', '/brain')).toBe(
      '/brain/api/spaces/docs/dynamic-apis/knowledge%20search/run',
    )
  })
})
