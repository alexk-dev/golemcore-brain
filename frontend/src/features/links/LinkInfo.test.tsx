import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { LinkInfo } from './LinkInfo'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'

const restorePageHistoryMock = vi.fn()

vi.mock('../../lib/api', () => ({
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
    restorePageHistoryMock.mockReset()
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
    })
  })

  it('shows version history and restores a selected version', async () => {
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

    fireEvent.click(screen.getByRole('button', { name: 'Restore Setup v2' }))

    await waitFor(() => {
      expect(restorePageHistoryMock).toHaveBeenCalledWith('guides/setup', 'v2')
    })
  })
})
