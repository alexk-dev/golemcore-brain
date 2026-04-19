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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { reindexAllSpaces, reindexSpace } from './api'

describe('api reindex endpoints', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({
      ok: true,
      status: 202,
      json: async () => ({ status: 'queued', spacesQueued: 1 }),
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uses the admin namespace for full and per-space reindex requests', async () => {
    await reindexSpace('engineering')
    await reindexAllSpaces()

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/admin/spaces/engineering/reindex',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/spaces/reindex',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
      }),
    )
  })
})
