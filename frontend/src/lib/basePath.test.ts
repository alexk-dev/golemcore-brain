import { describe, expect, it } from 'vitest'

import {
  detectAppBasePathFromBaseUri,
  isAppApiPath,
  normalizeAppBasePath,
  stripAppBasePath,
  withAppBasePath,
} from './basePath'

describe('basePath', () => {
  it('detects the deployed app base path from the document base URI', () => {
    expect(detectAppBasePathFromBaseUri('https://example.com/')).toBe('')
    expect(detectAppBasePathFromBaseUri('https://example.com/brain/')).toBe('/brain')
    expect(detectAppBasePathFromBaseUri('https://example.com/cloud/brain/')).toBe('/cloud/brain')
  })

  it('normalizes, applies, and strips app base paths for root-relative URLs', () => {
    expect(normalizeAppBasePath('/brain/')).toBe('/brain')
    expect(withAppBasePath('/api/config', '/brain')).toBe('/brain/api/config')
    expect(withAppBasePath('/brain/api/config', '/brain')).toBe('/brain/api/config')
    expect(withAppBasePath('https://example.com/api/config', '/brain')).toBe('https://example.com/api/config')
    expect(stripAppBasePath('/brain/api/config', '/brain')).toBe('/api/config')
    expect(isAppApiPath('/brain/api/config', '/brain')).toBe(true)
    expect(isAppApiPath('/brain/assets/index.js', '/brain')).toBe(false)
  })
})
