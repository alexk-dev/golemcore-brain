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
  beforeEach(() => {
    navigateMock.mockClear()
    useUiStore.setState({ currentUser: null, authDisabled: false, publicAccess: false })
  })

  it('does not prefill default credentials', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    expect(screen.getByLabelText('Username or email')).toHaveValue('')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('logs in and stores the current user', async () => {
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
