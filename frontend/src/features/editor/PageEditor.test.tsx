import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PageEditor } from './PageEditor'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'

const navigateMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
    useLocation: () => ({ pathname: '/e/guides/runbook' }),
  }
})

vi.mock('../assets/AssetManagerDialog', () => ({
  AssetManagerDialog: () => null,
}))

describe('PageEditor', () => {
  beforeEach(() => {
    navigateMock.mockReset()
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
      loadPageData: async () => undefined,
      savePage: async () => ({
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
      }),
      setTitle: () => undefined,
      setSlug: () => undefined,
      setContent: () => undefined,
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
})
