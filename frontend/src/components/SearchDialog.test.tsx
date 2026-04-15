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

import { render, screen, waitFor } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { WikiTreeNode } from '../types'
import { SearchDialog } from './SearchDialog'

vi.mock('../lib/api', () => ({
  searchPages: vi.fn(async () => [
    {
      id: 'guides/runbook',
      path: 'guides/runbook',
      title: 'Runbook',
      excerpt: 'Runbook excerpt',
      parentPath: 'guides',
      kind: 'PAGE',
    },
    {
      id: 'guides/setup',
      path: 'guides/setup',
      title: 'Setup',
      excerpt: 'Setup excerpt',
      parentPath: 'guides',
      kind: 'PAGE',
    },
  ]),
  getSearchStatus: vi.fn(async () => ({
    mode: 'live-scan',
    ready: true,
    indexedDocuments: 2,
    lastUpdatedAt: '2026-01-01T00:00:00Z',
  })),
}))

const tree: WikiTreeNode = {
  id: 'root',
  path: '',
  parentPath: null,
  title: 'Welcome',
  slug: '',
  kind: 'ROOT',
  hasChildren: true,
  children: [],
}

describe('SearchDialog', () => {
  it('supports keyboard navigation and enter selection', async () => {
    const navigated: string[] = []

    render(
      <SearchDialog
        open={true}
        tree={tree}
        onOpenChange={() => undefined}
        onNavigate={(path) => navigated.push(path)}
      />,
    )

    const input = screen.getByPlaceholderText('Search documentation, runbooks, and notes')
    fireEvent.change(input, { target: { value: 'run' } })

    await waitFor(() => {
      expect(screen.getByText('Runbook')).toBeInTheDocument()
    })

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(navigated.length).toBe(1)
  })

  it('renders as an embedded search pane without modal chrome', async () => {
    render(
      <SearchDialog
        open={true}
        tree={tree}
        embedded={true}
        onOpenChange={() => undefined}
        onNavigate={() => undefined}
      />,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search documentation, runbooks, and notes')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText(/Search mode: live-scan/i)).toBeInTheDocument()
    })
  })

  it('shows live search status details', async () => {
    render(
      <SearchDialog
        open={true}
        tree={tree}
        embedded={true}
        onOpenChange={() => undefined}
        onNavigate={() => undefined}
      />,
    )

    await waitFor(() => {
      expect(screen.getByText(/Search mode: live-scan/i)).toBeInTheDocument()
    })
    expect(screen.getByText(/2 documents indexed/i)).toBeInTheDocument()
  })
})
