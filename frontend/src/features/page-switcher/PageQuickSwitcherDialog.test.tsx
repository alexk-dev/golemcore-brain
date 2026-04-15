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
import { describe, expect, it, beforeEach } from 'vitest'

import { useTreeStore } from '../../stores/tree'
import { PageQuickSwitcherDialog } from './PageQuickSwitcherDialog'

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
        id: 'guides/setup',
        path: 'guides/setup',
        parentPath: 'guides',
        title: 'Setup',
        slug: 'setup',
        kind: 'PAGE',
        hasChildren: false,
        children: [],
      },
    ],
  })
})

describe('PageQuickSwitcherDialog', () => {
  it('filters pages and invokes navigation callback', () => {
    const navigated: string[] = []

    render(
      <PageQuickSwitcherDialog
        open={true}
        onOpenChange={() => undefined}
        onNavigate={(path) => navigated.push(path)}
      />,
    )

    fireEvent.change(screen.getByPlaceholderText('Type a page title…'), {
      target: { value: 'run' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Runbook/i }))
    expect(navigated).toEqual(['guides/runbook'])
  })

  it('supports keyboard navigation and enter selection', () => {
    const navigated: string[] = []

    render(
      <PageQuickSwitcherDialog
        open={true}
        onOpenChange={() => undefined}
        onNavigate={(path) => navigated.push(path)}
      />,
    )

    const input = screen.getByPlaceholderText('Type a page title…')
    fireEvent.change(input, { target: { value: 'g' } })
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(navigated.length).toBe(1)
  })
})
