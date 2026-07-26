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

import { render, screen, waitFor, within } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Tree row actions live behind a single overflow menu, so every action test has to open that
 * menu first. Radix opens the trigger from a keyboard event, which keeps the helper independent
 * of jsdom's pointer-event gaps.
 */
async function openNodeMenu(sidebar: HTMLElement, title: string) {
  fireEvent.keyDown(within(sidebar).getByRole('button', { name: `Actions for ${title}` }), { key: 'Enter' })
  return screen.findByRole('menu')
}

import App from '../../App'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

const convertPageMock = vi.hoisted(() => vi.fn())

vi.mock('../../lib/api', () => ({
  getAuthConfig: vi.fn(async () => ({
    authDisabled: true,
    publicAccess: true,
    user: null,
  })),
  getConfig: vi.fn(async () => ({
    publicAccess: true,
    hideLinkMetadataSection: false,
    authDisabled: true,
    maxAssetUploadSizeBytes: 1024,
    siteTitle: 'GolemCore Brain',
    rootPath: '',
    imageVersion: 'dev',
  })),
  getTree: vi.fn(async () => ({
    id: 'root',
    path: '',
    parentPath: null,
    title: 'Welcome',
    slug: '',
    kind: 'ROOT',
    hasChildren: true,
    children: [
      {
        id: 'guides',
        path: 'guides',
        parentPath: '',
        title: 'Guides',
        slug: 'guides',
        kind: 'SECTION',
        hasChildren: true,
        children: [
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
      },
      {
        id: 'product',
        path: 'product',
        parentPath: '',
        title: 'Product',
        slug: 'product',
        kind: 'SECTION',
        hasChildren: true,
        children: [
          {
            id: 'product/roadmap',
            path: 'product/roadmap',
            parentPath: 'product',
            title: 'Roadmap',
            slug: 'roadmap',
            kind: 'PAGE',
            hasChildren: false,
            children: [],
          },
        ],
      },
    ],
  })),
  getPageByPath: vi.fn(async (path: string) => {
    if (path === 'guides' || path === 'guides/setup') {
      return {
        id: path,
        path,
        parentPath: path === 'guides' ? '' : 'guides',
        title: path === 'guides' ? 'Guides' : 'Setup',
        slug: path.split('/').pop() || '',
        kind: path === 'guides' ? 'SECTION' : 'PAGE',
        content: '# Guides',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        children: path === 'guides'
          ? [
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
            ]
          : [],
      }
    }
    if (path === 'product' || path === 'product/roadmap') {
      return {
        id: path,
        path,
        parentPath: path === 'product' ? '' : 'product',
        title: path === 'product' ? 'Product' : 'Roadmap',
        slug: path.split('/').pop() || '',
        kind: path === 'product' ? 'SECTION' : 'PAGE',
        content: '# Product',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        children: path === 'product'
          ? [
              {
                id: 'product/roadmap',
                path: 'product/roadmap',
                parentPath: 'product',
                title: 'Roadmap',
                slug: 'roadmap',
                kind: 'PAGE',
                hasChildren: false,
                children: [],
              },
            ]
          : [],
      }
    }
    return {
      id: 'root',
      path: '',
      parentPath: '',
      title: 'Welcome',
      slug: '',
      kind: 'ROOT',
      content: '# Welcome',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      children: [],
    }
  }),
  getLinkStatus: vi.fn(async () => ({
    backlinks: [],
    brokenIncoming: [],
    outgoings: [],
    brokenOutgoings: [],
  })),
  getPageHistoryVersion: vi.fn(),
  getSearchStatus: vi.fn(async () => ({
    mode: 'live-scan',
    ready: true,
    indexedDocuments: 1,
    lastUpdatedAt: '2026-01-01T00:00:00Z',
  })),
  logout: vi.fn(async () => ({ message: 'Logged out', user: null })),
  changePassword: vi.fn(async () => ({ message: 'Password changed', user: null })),
  createPage: vi.fn(),
  deletePage: vi.fn(),
  movePage: vi.fn(),
  copyPage: vi.fn(),
  convertPage: (...args: unknown[]) => convertPageMock(...args),
  sortSection: vi.fn(),
  ensurePage: vi.fn(),
  lookupPath: vi.fn(),
  updatePage: vi.fn(),
  listAssets: vi.fn(async () => []),
  uploadAsset: vi.fn(),
  renameAsset: vi.fn(),
  deleteAsset: vi.fn(),
  searchPages: vi.fn(async () => []),
  listUsers: vi.fn(async () => []),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUserAccount: vi.fn(),
  planMarkdownImport: vi.fn(async () => ({ items: [] })),
  applyMarkdownImport: vi.fn(async () => ({ importedCount: 0, createdCount: 0, updatedCount: 0, skippedCount: 0, items: [] })),
}))

describe('Sidebar tree interaction parity', () => {
  beforeEach(() => {
    convertPageMock.mockReset()
    convertPageMock.mockResolvedValue({
      id: 'product/roadmap',
      path: 'product/roadmap',
      parentPath: 'product',
      title: 'Roadmap',
      slug: 'roadmap',
      kind: 'SECTION',
      content: '# Product',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      children: [],
    })
    useTreeStore.setState({
      tree: null,
      loading: false,
      error: null,
      activeNodeId: null,
      openNodeIdSet: {},
      byPath: {},
      byId: {},
      flatPages: [],
      manualNodeStateById: {},
      mustOpenNodeIdSet: {},
      suggestedOpenNodeIdSet: {},
    })
    useViewerStore.setState({ page: null, linkStatus: null, history: [], loading: false, error: null })
    useEditorStore.setState({
      page: null,
      initialPage: null,
      title: '',
      slug: '',
      content: '',
      loading: false,
      error: null,
    })
    useUiStore.setState({
      isDark: false,
      sidebarVisible: true,
      searchOpen: false,
      quickSwitcherOpen: false,
      authDisabled: true,
      publicAccess: true,
      currentUser: null,
    })
  })

  it('collapses route-only branches but preserves manual branch expansion across navigation', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand section' }))
    await waitFor(() => {
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Roadmap' }))
    await waitFor(() => {
      expect(within(sidebar).queryByText('Setup')).not.toBeInTheDocument()
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })
  })

  it('keeps a manually reopened active branch open after navigating away', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Guides' }))
    expect(within(sidebar).queryByText('Setup')).not.toBeInTheDocument()

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Guides' }))
    expect(within(sidebar).getByText('Setup')).toBeInTheDocument()

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand section' }))
    await waitFor(() => {
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Roadmap' }))
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })
  })

  it('opens the selected tree node directly in the editor', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand section' }))

    await waitFor(() => {
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })

    const menu = await openNodeMenu(sidebar, 'Roadmap')
    fireEvent.click(within(menu).getByRole('menuitem', { name: 'Edit Roadmap' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Close editor' })).toBeInTheDocument()
    })
    expect(screen.getAllByText('/product/roadmap')).toHaveLength(2)
  })

  it('opens page operations from tree node actions', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand section' }))

    await waitFor(() => {
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })

    const roadmapMenu = await openNodeMenu(sidebar, 'Roadmap')
    fireEvent.click(within(roadmapMenu).getByRole('menuitem', { name: 'Move Roadmap' }))

    expect(await screen.findByText('Move page')).toBeInTheDocument()
    expect(screen.getByText('Current path: /product/roadmap')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    const productMenu = await openNodeMenu(sidebar, 'Product')
    fireEvent.click(within(productMenu).getByRole('menuitem', { name: 'Sort Product' }))

    expect(await screen.findByText('Sort child pages')).toBeInTheDocument()
    expect(screen.getByText('Section: /product')).toBeInTheDocument()
  })

  it('expands, collapses, and sorts from the tree toolbar', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand all' }))
    expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Collapse all' }))
    await waitFor(() => {
      expect(within(sidebar).queryByText('Roadmap')).not.toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Sort root pages' }))
    expect(await screen.findByText('Sort child pages')).toBeInTheDocument()
    expect(screen.getByText('Section: /')).toBeInTheDocument()
  })

  it('shows exactly one search surface when the sidebar search tab is selected', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
    })

    fireEvent.click(within(sidebar).getByRole('tab', { name: 'Search' }))

    // The tab swaps the sidebar panel in place; it must not also raise the modal search dialog.
    await waitFor(() => {
      expect(screen.getAllByPlaceholderText('Search documentation, runbooks, and notes')).toHaveLength(1)
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps row actions behind a single overflow trigger and closes the menu on Escape', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    await waitFor(() => {
      expect(within(sidebar).getByText('Setup')).toBeInTheDocument()
    })

    // The row itself offers navigation plus one overflow trigger, so nothing destructive sits
    // next to the title where it can be hit by accident.
    expect(within(sidebar).queryByRole('button', { name: 'Delete Setup' })).not.toBeInTheDocument()
    expect(within(sidebar).queryByRole('button', { name: 'Edit Setup' })).not.toBeInTheDocument()
    expect(within(sidebar).getByRole('button', { name: 'Actions for Setup' })).toBeInTheDocument()

    const menu = await openNodeMenu(sidebar, 'Setup')
    expect(within(menu).getByRole('menuitem', { name: 'Delete Setup' })).toBeInTheDocument()

    fireEvent.keyDown(menu, { key: 'Escape' })
    await waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
  })

  it('converts a tree page to a section from node actions', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    fireEvent.click(within(sidebar).getByRole('button', { name: 'Expand section' }))

    await waitFor(() => {
      expect(within(sidebar).getByText('Roadmap')).toBeInTheDocument()
    })

    const menu = await openNodeMenu(sidebar, 'Roadmap')
    fireEvent.click(within(menu).getByRole('menuitem', { name: 'Convert Roadmap to section' }))

    await waitFor(() => {
      expect(convertPageMock).toHaveBeenCalledWith('product/roadmap', { targetKind: 'SECTION' })
    })
  })
})
