import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PageEditor } from './PageEditor'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'

const navigateMock = vi.fn()
const uploadAssetMock = vi.fn()
const setContentMock = vi.fn()
const savePageMock = vi.fn()
const reloadFromConflictMock = vi.fn()
const mergeConflictWithLocalDraftMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
    useLocation: () => ({ pathname: '/e/guides/runbook' }),
  }
})

vi.mock('../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api')>('../../lib/api')
  return {
    ...actual,
    uploadAsset: (...args: unknown[]) => uploadAssetMock(...args),
  }
})

vi.mock('../assets/AssetManagerDialog', () => ({
  AssetManagerDialog: () => null,
}))

describe('PageEditor', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    uploadAssetMock.mockReset()
    setContentMock.mockReset()
    savePageMock.mockReset()
    reloadFromConflictMock.mockReset()
    mergeConflictWithLocalDraftMock.mockReset()
    const treeNode = {
      id: 'guides/runbook',
      path: 'guides/runbook',
      parentPath: 'guides',
      title: 'Runbook',
      slug: 'runbook',
      kind: 'PAGE' as const,
      hasChildren: false,
      children: [],
    }
    useTreeStore.setState({
      tree: null,
      loading: false,
      error: null,
      activeNodeId: null,
      openNodeIdSet: {},
      byPath: { 'guides/runbook': treeNode },
      byId: { 'guides/runbook': treeNode },
      flatPages: [treeNode],
      manualNodeStateById: {},
      mustOpenNodeIdSet: {},
      suggestedOpenNodeIdSet: {},
      reloadTree: async () => undefined,
      toggleNode: () => undefined,
      openNode: () => undefined,
      closeNode: () => undefined,
      setActiveNodeId: () => undefined,
      getPageByPath: (path) => (path === 'guides/runbook' ? treeNode : null),
      getPageById: () => null,
      openAncestorsForPath: () => undefined,
    })
    useEditorStore.setState({
      page: {
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
      },
      initialPage: {
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
      },
      title: 'Runbook',
      slug: 'runbook',
      content: 'Initial content',
      loading: false,
      error: null,
      conflict: null,
      loadPageData: async () => undefined,
      savePage: savePageMock,
      reloadFromConflict: reloadFromConflictMock,
      mergeConflictWithLocalDraft: mergeConflictWithLocalDraftMock,
      setTitle: () => undefined,
      setSlug: () => undefined,
      setContent: setContentMock,
    })
    savePageMock.mockResolvedValue({
      id: 'guides/runbook',
      path: 'guides/runbook',
      parentPath: 'guides',
      title: 'Runbook',
      slug: 'runbook',
      kind: 'PAGE',
      content: 'Updated content',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      children: [],
    })
  })

  it('shows metadata panel and unsaved changes dialog when closing dirty editor', () => {
    useEditorStore.setState({ content: 'Changed content' })

    render(
      <MemoryRouter>
        <PageEditor />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Edit metadata' }))
    expect(screen.getByText('Metadata')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Close editor' }))
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalledWith('/guides/runbook')
  })

  it('uploads pasted image assets into the current page', async () => {
    const file = new File(['image-bytes'], 'pasted-image.png', { type: 'image/png' })
    uploadAssetMock.mockResolvedValue({
      name: 'pasted-image.png',
      path: '/api/assets?path=guides/runbook&name=pasted-image.png',
      size: 1024,
      contentType: 'image/png',
    })

    const { container } = render(
      <MemoryRouter>
        <PageEditor />
      </MemoryRouter>,
    )

    const editor = container.querySelector('.markdown-code-editor')
    expect(editor).not.toBeNull()

    fireEvent.paste(editor as Element, {
      clipboardData: {
        files: [file],
        items: [
          {
            kind: 'file',
            type: 'image/png',
            getAsFile: () => file,
          },
        ],
      },
    })

    await waitFor(() => {
      expect(uploadAssetMock).toHaveBeenCalledWith('guides/runbook', file)
    })
    await waitFor(() => {
      expect(setContentMock).toHaveBeenCalled()
    })
  })

  it('shows conflict recovery actions when another session updated the page', () => {
    useEditorStore.setState({
      conflict: {
        id: 'guides/runbook',
        path: 'guides/runbook',
        parentPath: 'guides',
        title: 'Runbook from server',
        slug: 'runbook',
        kind: 'PAGE',
        content: 'Server content',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T12:00:00Z',
        revision: 'server-revision',
        children: [],
      },
    })

    render(
      <MemoryRouter>
        <PageEditor />
      </MemoryRouter>,
    )

    expect(screen.getByText('Page changed in another session')).toBeInTheDocument()
    expect(screen.getByText('Latest saved version')).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('button', { name: 'Merge with latest' })[0])
    expect(mergeConflictWithLocalDraftMock).toHaveBeenCalledTimes(1)
  })
})
