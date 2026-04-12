import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SpacesPage } from './SpacesPage'
import { useSpaceStore } from '../../stores/space'
import { useUiStore } from '../../stores/ui'

vi.mock('../../lib/api', () => ({
  createSpace: vi.fn(),
  deleteSpace: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('SpacesPage', () => {
  beforeEach(() => {
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
})
