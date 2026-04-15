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

import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import { InsertWikiLinkDialog } from './InsertWikiLinkDialog'
import { useTreeStore } from '../stores/tree'

beforeEach(() => {
  useTreeStore.setState({
    tree: null,
    loading: false,
    error: null,
    activeNodeId: null,
    openNodeIdSet: {},
    byPath: {},
    byId: {},
    flatPages: [
      {
        id: 'guides',
        path: 'guides',
        parentPath: '',
        title: 'Guides',
        slug: 'guides',
        kind: 'SECTION',
        hasChildren: true,
        children: [],
      },
      {
        id: 'guides/runbook',
        path: 'guides/runbook',
        parentPath: 'guides',
        title: 'Runbook',
        slug: 'runbook',
        kind: 'PAGE',
        hasChildren: false,
        children: [],
      },
      {
        id: 'guides/writing-notes',
        path: 'guides/writing-notes',
        parentPath: 'guides',
        title: 'Writing notes',
        slug: 'writing-notes',
        kind: 'PAGE',
        hasChildren: false,
        children: [],
      },
    ],
    manualNodeStateById: {},
    mustOpenNodeIdSet: {},
    suggestedOpenNodeIdSet: {},
  })
})

describe('InsertWikiLinkDialog', () => {
  it('seeds the query from initialQuery and returns the picked page', () => {
    const picked: Array<{ path: string; title: string }> = []

    render(
      <InsertWikiLinkDialog
        open={true}
        initialQuery="run"
        onOpenChange={() => undefined}
        onSelect={(page) => picked.push({ path: page.path, title: page.title })}
      />,
    )

    const input = screen.getByPlaceholderText('Type a page title or path…') as HTMLInputElement
    expect(input.value).toBe('run')

    fireEvent.click(screen.getByRole('button', { name: /Runbook/i }))
    expect(picked).toEqual([{ path: 'guides/runbook', title: 'Runbook' }])
  })

  it('selects the highlighted result with Enter', () => {
    const picked: Array<{ path: string; title: string }> = []

    render(
      <InsertWikiLinkDialog
        open={true}
        onOpenChange={() => undefined}
        onSelect={(page) => picked.push({ path: page.path, title: page.title })}
      />,
    )

    const input = screen.getByPlaceholderText('Type a page title or path…')
    fireEvent.change(input, { target: { value: 'writing' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(picked).toEqual([{ path: 'guides/writing-notes', title: 'Writing notes' }])
  })
})
