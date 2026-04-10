import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { useEditorStore } from './stores/editor'
import { useTreeStore } from './stores/tree'
import { useUiStore } from './stores/ui'
import { useViewerStore } from './stores/viewer'

vi.mock('./lib/api', () => ({
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
})
