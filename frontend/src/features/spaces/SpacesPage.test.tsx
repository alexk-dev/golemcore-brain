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

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SpacesPage } from './SpacesPage'
import { reindexAllSpaces, reindexSpace } from '../../lib/api'
import { useSpaceStore } from '../../stores/space'
import { useUiStore } from '../../stores/ui'

vi.mock('../../lib/api', () => ({
  createSpace: vi.fn(),
  deleteSpace: vi.fn(),
  reindexAllSpaces: vi.fn(),
  reindexSpace: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('SpacesPage', () => {
  beforeEach(() => {
    vi.mocked(reindexAllSpaces).mockReset()
    vi.mocked(reindexSpace).mockReset()
    vi.mocked(reindexAllSpaces).mockResolvedValue({ status: 'queued', spacesQueued: 1 })
    vi.mocked(reindexSpace).mockResolvedValue({ status: 'queued', spacesQueued: 1 })
    useUiStore.setState({
      authDisabled: false,
      currentUser: {
        id: 'admin-1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
    })
    useSpaceStore.setState({
      spaces: [
        {
          id: 'space-1',
          slug: 'docs',
          name: 'Docs',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      activeSlug: 'docs',
      loaded: true,
      reloadSpaces: vi.fn(async () => undefined),
    })
  })

  it('links each space to its own settings screen', () => {
    render(
      <MemoryRouter>
        <SpacesPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Settings' })).toHaveAttribute(
      'href',
      '/spaces/docs/settings',
    )
  })

  it('queues reindex for one space and all spaces', async () => {
    render(
      <MemoryRouter>
        <SpacesPage />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Reindex' }))
    await waitFor(() => {
      expect(reindexSpace).toHaveBeenCalledWith('docs')
    })

    fireEvent.click(screen.getByRole('button', { name: 'Full reindex' }))
    await waitFor(() => {
      expect(reindexAllSpaces).toHaveBeenCalledTimes(1)
    })
  })
})
