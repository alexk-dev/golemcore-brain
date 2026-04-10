import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { LinkInfo } from './LinkInfo'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

const restorePageHistoryMock = vi.fn()
const getPageHistoryVersionMock = vi.fn()
const refreshPageDataMock = vi.fn()
const reloadTreeMock = vi.fn()

vi.mock('../../lib/api', () => ({
  getPageHistoryVersion: (...args: unknown[]) => getPageHistoryVersionMock(...args),
  restorePageHistory: (...args: unknown[]) => restorePageHistoryMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('LinkInfo', () => {
  beforeEach(() => {
    getPageHistoryVersionMock.mockReset()
    restorePageHistoryMock.mockReset()
    refreshPageDataMock.mockReset()
    reloadTreeMock.mockReset()
    useTreeStore.setState({
      reloadTree: reloadTreeMock,
    })
    useUiStore.setState({
      authDisabled: true,
      currentUser: null,
    })
    useViewerStore.setState({
      page: {
        id: 'guides/setup',
        path: 'guides/setup',
        parentPath: 'guides',
        title: 'Setup',
        slug: 'setup',
        kind: 'PAGE',
        content: 'Current content',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-03T00:00:00Z',
        children: [],
      },
      linkStatus: {
        backlinks: [],
        brokenIncoming: [],
        outgoings: [],
        brokenOutgoings: [],
      },
      history: [
        {
          id: 'v2',
          title: 'Setup v2',
          slug: 'setup',
          recordedAt: '2026-01-02T00:00:00Z',
        },
        {
          id: 'v1',
          title: 'Setup v1',
          slug: 'setup',
          recordedAt: '2026-01-01T00:00:00Z',
        },
      ],
      loading: false,
      error: null,
      loadPageData: vi.fn(async () => undefined),
      refreshPageData: refreshPageDataMock,
    })
  })

  it('shows version history and restores a selected version', async () => {
    getPageHistoryVersionMock.mockResolvedValue({
      id: 'v2',
      title: 'Setup v2',
      slug: 'setup',
      content: 'Version two',
      recordedAt: '2026-01-02T00:00:00Z',
      author: 'alex',
      reason: 'Manual save',
      summary: 'Updated content.',
    })
    restorePageHistoryMock.mockResolvedValue({
      id: 'guides/setup',
      path: 'guides/setup',
      parentPath: 'guides',
      title: 'Setup v2',
      slug: 'setup',
      kind: 'PAGE',
      content: 'Version two',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-04T00:00:00Z',
      children: [],
    })

    render(<LinkInfo />)

    expect(screen.getByText('Version history')).toBeInTheDocument()
    expect(screen.getByText('Setup v2')).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('button', { name: 'Preview' })[0])

    await waitFor(() => {
      expect(getPageHistoryVersionMock).toHaveBeenCalledWith('guides/setup', 'v2')
    })
    expect(screen.getByText(/Version preview: Setup v2/i)).toBeInTheDocument()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button'))

    await waitFor(() => {
      expect(screen.queryByText(/Version preview: Setup v2/i)).not.toBeInTheDocument()
    })

    fireEvent.click(screen.getAllByRole('button', { name: 'Restore' })[0])
    fireEvent.click(screen.getByRole('button', { name: 'Restore version' }))

    await waitFor(() => {
      expect(restorePageHistoryMock).toHaveBeenCalledWith('guides/setup', 'v2')
    })
    await waitFor(() => {
      expect(reloadTreeMock).toHaveBeenCalledTimes(1)
    })
    await waitFor(() => {
      expect(refreshPageDataMock).toHaveBeenCalledWith('guides/setup')
    })
  })
})
