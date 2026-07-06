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

import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import * as api from './lib/api'
import { useEditorStore } from './stores/editor'
import { useSpaceStore } from './stores/space'
import { useTreeStore } from './stores/tree'
import { useUiStore } from './stores/ui'
import { useViewerStore } from './stores/viewer'

vi.mock('./lib/api', () => ({
  setCurrentSpaceSlug: vi.fn(),
  listSpaces: vi.fn(async () => [
    { id: 's1', slug: 'default', name: 'Default', createdAt: '2026-01-01T00:00:00Z' },
    { id: 's2', slug: 'docs', name: 'Docs', createdAt: '2026-01-01T00:00:00Z' },
  ]),
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
    imageVersion: 'sha-1234567',
  })),
  login: vi.fn(async () => ({
    message: 'Logged in',
    user: {
      id: '1',
      username: 'admin',
      email: 'admin@example.com',
      role: 'ADMIN',
    },
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
        hasChildren: false,
        children: [],
      },
    ],
  })),
  getPageByPath: vi.fn(async (path: string) => ({
    id: path || 'root',
    path,
    parentPath: path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '',
    title: path ? 'Guides' : 'Welcome',
    slug: path.split('/').pop() || '',
    kind: path ? 'SECTION' : 'ROOT',
    content: '# Demo\n\nLoaded page',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    children: [],
  })),
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
  createPage: vi.fn(),
  deletePage: vi.fn(),
  movePage: vi.fn(),
  copyPage: vi.fn(),
  convertPage: vi.fn(),
  sortSection: vi.fn(),
  ensurePage: vi.fn(),
  lookupPath: vi.fn(),
  updatePage: vi.fn(),
  listAssets: vi.fn(async () => []),
  uploadAsset: vi.fn(),
  renameAsset: vi.fn(),
  deleteAsset: vi.fn(),
  searchPages: vi.fn(async () => []),
  planMarkdownImport: vi.fn(async () => ({ items: [] })),
  applyMarkdownImport: vi.fn(async () => ({ importedCount: 0, createdCount: 0, updatedCount: 0, skippedCount: 0, items: [] })),
}));

describe('App', () => {
  beforeEach(() => {
    vi.mocked(api.listSpaces).mockClear()
    vi.mocked(api.listSpaces).mockResolvedValue([
      { id: 's1', slug: 'default', name: 'Default', createdAt: '2026-01-01T00:00:00Z' },
      { id: 's2', slug: 'docs', name: 'Docs', createdAt: '2026-01-01T00:00:00Z' },
    ])
    vi.mocked(api.getAuthConfig).mockClear()
    vi.mocked(api.getAuthConfig).mockResolvedValue({
      authDisabled: true,
      publicAccess: true,
      user: null,
    })
    vi.mocked(api.login).mockClear()
    vi.mocked(api.login).mockResolvedValue({
      message: 'Logged in',
      user: {
        id: '1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
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
    useSpaceStore.setState({
      spaces: [],
      activeSlug: 'default',
      loaded: false,
    })
    useUiStore.setState({
      isDark: false,
      sidebarVisible: true,
      searchOpen: false,
      quickSwitcherOpen: false,
      authDisabled: true,
      publicAccess: true,
      currentUser: null,
      authResolved: false,
    })
  })

  it('renders a subtle image version marker from config', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    const sidebar = await screen.findByTestId('sidebar')
    expect(within(sidebar).getByTitle('Image version sha-1234567')).toHaveTextContent('sha-1234567')
    expect(within(screen.getByRole('banner')).queryByTitle('Image version sha-1234567')).not.toBeInTheDocument()
  })

  it('shows a standalone space switcher when there is no account menu', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('button', { name: 'Switch space, current space Default' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Account menu/ })).not.toBeInTheDocument()
  })

  it('loads spaces after login so the account menu can switch spaces immediately', async () => {
    const user = userEvent.setup()
    vi.mocked(api.getAuthConfig).mockResolvedValueOnce({
      authDisabled: false,
      publicAccess: false,
      user: null,
    })
    vi.mocked(api.listSpaces).mockClear()

    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    )

    const signInButton = await screen.findByRole('button', { name: /Sign in/i })
    expect(api.listSpaces).not.toHaveBeenCalled()

    fireEvent.submit(signInButton)

    await waitFor(() => {
      expect(api.listSpaces).toHaveBeenCalledTimes(1)
    })

    await user.click(await screen.findByRole('button', { name: /Account menu for admin/i }))

    const group = await screen.findByRole('group', { name: /Switch space/i })
    expect(within(group).getByRole('menuitemradio', { name: 'Default' })).toBeInTheDocument()
    expect(within(group).getByRole('menuitemradio', { name: 'Docs' })).toBeInTheDocument()
  })

  it('renders the shell and loads the root page without crashing', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('GolemCore Brain')).toBeInTheDocument()
    })

    expect(screen.getByText('Tree')).toBeInTheDocument()
  })

  it('supports viewer and shell keyboard shortcuts', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Tree')).toBeInTheDocument()
    })

    fireEvent.keyDown(window, { key: 'E', ctrlKey: true, shiftKey: true })

    await waitFor(() => {
      expect(screen.queryByText('Tree')).not.toBeInTheDocument()
    })

    fireEvent.keyDown(window, { key: 'e', ctrlKey: true })

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Close editor' })).toBeInTheDocument()
    })
  })

  it('surfaces a global edit action for the current page', async () => {
    render(
      <MemoryRouter initialEntries={['/guides']}>
        <App />
      </MemoryRouter>,
    )

    const editButton = await screen.findByRole('button', { name: 'Edit page' })
    fireEvent.click(editButton)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Close editor' })).toBeInTheDocument()
    })
  })

  it('opens the root page editor from the global edit action', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Edit page' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Close editor' })).toBeInTheDocument()
    })
  })
})
