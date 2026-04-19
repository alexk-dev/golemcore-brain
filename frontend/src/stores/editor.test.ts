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

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useEditorStore } from './editor'
import type { WikiPage } from '../types'

const getPageByPathMock = vi.hoisted(() => vi.fn())
const updatePageMock = vi.hoisted(() => vi.fn())

vi.mock('../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../lib/api')>('../lib/api')
  return {
    ...actual,
    getPageByPath: getPageByPathMock,
    updatePage: updatePageMock,
  }
})

describe('editor store', () => {
  beforeEach(() => {
    getPageByPathMock.mockReset()
    updatePageMock.mockReset()
    useEditorStore.setState({
      page: null,
      initialPage: null,
      title: '',
      slug: '',
      content: '',
      loading: false,
      error: null,
      conflict: null,
    })
  })

  it('saves changed editor content through the page update API', async () => {
    const currentPage = page({
      content: 'Initial content',
      revision: 'revision-1',
    })
    const updatedPage = page({
      content: 'Changed content',
      revision: 'revision-2',
    })
    updatePageMock.mockResolvedValue(updatedPage)
    useEditorStore.setState({
      page: currentPage,
      initialPage: { ...currentPage },
      title: 'Runbook',
      slug: 'runbook',
      content: 'Changed content',
    })

    const result = await useEditorStore.getState().savePage()

    expect(updatePageMock).toHaveBeenCalledWith('guides/runbook', {
      title: 'Runbook',
      slug: 'runbook',
      content: 'Changed content',
      revision: 'revision-1',
    })
    expect(result).toEqual(updatedPage)
    expect(useEditorStore.getState().initialPage?.revision).toBe('revision-2')
  })
})

function page(overrides: Partial<WikiPage>): WikiPage {
  return {
    id: 'guides/runbook',
    path: 'guides/runbook',
    parentPath: 'guides',
    title: 'Runbook',
    slug: 'runbook',
    kind: 'PAGE',
    content: 'Initial content',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    children: [],
    ...overrides,
  }
}
