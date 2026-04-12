import { describe, expect, it } from 'vitest'

import { buildDynamicApiRunPath } from './dynamicApiUrls'

describe('dynamic API URLs', () => {
  it('builds a space-scoped run endpoint path', () => {
    expect(buildDynamicApiRunPath('docs', 'knowledge-search')).toBe(
      '/api/spaces/docs/dynamic-apis/knowledge-search/run',
    )
  })
})
