import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { LoginPage } from './LoginPage'
import { useUiStore } from '../../stores/ui'

const navigateMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
  }
})

vi.mock('../../lib/api', () => ({
  login: vi.fn(async () => ({
    message: 'Logged in',
    user: {
      id: '1',
      username: 'admin',
      email: 'admin@example.com',
      role: 'ADMIN',
    },
  })),
}))

describe('LoginPage', () => {
  it('logs in and stores the current user', async () => {
    useUiStore.setState({ currentUser: null, authDisabled: false, publicAccess: false })
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    fireEvent.submit(screen.getByRole('button', { name: /Sign in/i }))

    await waitFor(() => {
      expect(useUiStore.getState().currentUser?.username).toBe('admin')
    })
    expect(navigateMock).toHaveBeenCalledWith('/')
  })
})
