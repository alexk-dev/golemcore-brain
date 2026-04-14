import { render, screen, waitFor, within } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Edit Roadmap' }))

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

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Move Roadmap' }))

    expect(await screen.findByText('Move page')).toBeInTheDocument()
    expect(screen.getByText('Current path: /product/roadmap')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Sort Product' }))

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

    fireEvent.click(within(sidebar).getByRole('button', { name: 'Convert Roadmap to section' }))

    await waitFor(() => {
      expect(convertPageMock).toHaveBeenCalledWith('product/roadmap', { targetKind: 'SECTION' })
    })
  })
})
