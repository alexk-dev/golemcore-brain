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
