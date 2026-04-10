import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from '../../App'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

const ensurePageMock = vi.fn()

vi.mock('../../lib/api', () => ({
  getAuthConfig: vi.fn(async () => ({
    authDisabled: false,
    publicAccess: true,
    user: null,
  })),
  getConfig: vi.fn(async () => ({
    publicAccess: true,
    hideLinkMetadataSection: false,
    authDisabled: false,
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
  getPageByPath: vi.fn(async (path: string) => {
    if (path === 'missing-page') {
      throw new Error('Page not found')
    }
    return {
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
    }
  }),
  getLinkStatus: vi.fn(async () => ({
    backlinks: [],
    brokenIncoming: [],
    outgoings: [],
    brokenOutgoings: [],
  })),
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
  sortSection: vi.fn(),
  ensurePage: (...args: unknown[]) => ensurePageMock(...args),
  lookupPath: vi.fn(),
  updatePage: vi.fn(),
  listAssets: vi.fn(async () => []),
  uploadAsset: vi.fn(),
  renameAsset: vi.fn(),
  deleteAsset: vi.fn(),
  searchPages: vi.fn(async () => []),
  logout: vi.fn(async () => ({ message: 'Logged out', user: null })),
  changePassword: vi.fn(async () => ({ message: 'Password changed', user: null })),
  listUsers: vi.fn(async () => []),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUserAccount: vi.fn(),
  planMarkdownImport: vi.fn(async () => ({ items: [] })),
  applyMarkdownImport: vi.fn(async () => ({ importedCount: 0, createdCount: 0, updatedCount: 0, skippedCount: 0, items: [] })),
}))

describe('Public read-only mode', () => {
  beforeEach(() => {
    ensurePageMock.mockReset()
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
      authDisabled: false,
      publicAccess: true,
      currentUser: null,
    })
  })

  it('hides create affordances for anonymous public readers', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('GolemCore Brain')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: 'New page' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New section' })).not.toBeInTheDocument()
    expect(screen.queryByText('Account')).not.toBeInTheDocument()
    expect(screen.queryByText('Logout')).not.toBeInTheDocument()
  })

  it('shows sign-in CTA instead of create-by-path for missing pages', async () => {
    render(
      <MemoryRouter initialEntries={['/missing-page']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Page not found')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: /Create page by path/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Sign in to create this page/i })).toBeInTheDocument()
  })

  it('redirects anonymous editor access to sign-in guidance', async () => {
    render(
      <MemoryRouter initialEntries={['/e/guides']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Sign in required')).toBeInTheDocument()
    })

    expect(screen.getByRole('link', { name: /Sign in to edit/i })).toBeInTheDocument()
  })
})
