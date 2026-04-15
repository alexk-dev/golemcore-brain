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

import { AccountPage } from './AccountPage'
import { useUiStore } from '../../stores/ui'

const navigateMock = vi.fn()
const changePasswordMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigateMock,
  }
})

vi.mock('../../lib/api', () => ({
  changePassword: (...args: unknown[]) => changePasswordMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('AccountPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUiStore.setState({
      currentUser: {
        id: 'admin-1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
    })
  })

  it('changes password and redirects to login', async () => {
    changePasswordMock.mockResolvedValue({ message: 'Password changed', user: null })

    render(
      <MemoryRouter>
        <AccountPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Current password'), {
      target: { value: 'admin' },
    })
    fireEvent.change(screen.getByLabelText('New password'), {
      target: { value: 'new-admin-pass' },
    })
    fireEvent.change(screen.getByLabelText('Confirm password'), {
      target: { value: 'new-admin-pass' },
    })
    fireEvent.submit(screen.getByRole('button', { name: 'Change password' }))

    await waitFor(() => {
      expect(changePasswordMock).toHaveBeenCalledWith('admin', 'new-admin-pass')
    })
    await waitFor(() => {
      expect(useUiStore.getState().currentUser).toBeNull()
    })
    expect(navigateMock).toHaveBeenCalledWith('/login')
  })
})
