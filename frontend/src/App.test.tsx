import { render, screen, waitFor } from '@testing-library/react'
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
  createPage: vi.fn(),
  deletePage: vi.fn(),
  movePage: vi.fn(),
  copyPage: vi.fn(),
  sortSection: vi.fn(),
  ensurePage: vi.fn(),
  lookupPath: vi.fn(),
  updatePage: vi.fn(),
  listAssets: vi.fn(async () => []),
  uploadAsset: vi.fn(),
  renameAsset: vi.fn(),
  deleteAsset: vi.fn(),
  searchPages: vi.fn(async () => []),
}))

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
    })
    useViewerStore.setState({ page: null, linkStatus: null, loading: false, error: null })
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
})
