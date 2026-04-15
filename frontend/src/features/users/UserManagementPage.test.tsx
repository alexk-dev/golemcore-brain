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
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { UserManagementPage } from './UserManagementPage'
import { useUiStore } from '../../stores/ui'
import type { PublicUserView } from '../../types'

const listUsersMock = vi.fn()
const createUserMock = vi.fn()
const updateUserMock = vi.fn()
const deleteUserAccountMock = vi.fn()

vi.mock('../../lib/api', () => ({
  listUsers: (...args: unknown[]) => listUsersMock(...args),
  createUser: (...args: unknown[]) => createUserMock(...args),
  updateUser: (...args: unknown[]) => updateUserMock(...args),
  deleteUserAccount: (...args: unknown[]) => deleteUserAccountMock(...args),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('UserManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('confirm', vi.fn(() => true))
    useUiStore.setState({
      currentUser: {
        id: 'admin-1',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
    })
  })

  it('edits and deletes users from the admin page', async () => {
    const adminUser: PublicUserView = {
      id: 'admin-1',
      username: 'admin',
      email: 'admin@example.com',
      role: 'ADMIN',
    }
    const editorUser: PublicUserView = {
      id: 'editor-1',
      username: 'editor',
      email: 'editor@example.com',
      role: 'EDITOR',
    }
    const updatedEditorUser: PublicUserView = {
      id: 'editor-1',
      username: 'editor',
      email: 'updated@example.com',
      role: 'VIEWER',
    }

    listUsersMock
      .mockResolvedValueOnce([adminUser, editorUser])
      .mockResolvedValueOnce([adminUser, updatedEditorUser])
      .mockResolvedValueOnce([adminUser])
    updateUserMock.mockResolvedValue(updatedEditorUser)
    deleteUserAccountMock.mockResolvedValue(undefined)

    render(<UserManagementPage />)

    expect(await screen.findByText('editor')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit editor' }))
    fireEvent.change(screen.getAllByLabelText('Email')[1], {
      target: { value: 'updated@example.com' },
    })
    fireEvent.change(screen.getAllByLabelText('Role')[1], {
      target: { value: 'VIEWER' },
    })
    fireEvent.change(screen.getAllByLabelText('Password')[1], {
      target: { value: 'updated-pass' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => {
      expect(updateUserMock).toHaveBeenCalledWith('editor-1', {
        username: 'editor',
        email: 'updated@example.com',
        password: 'updated-pass',
        role: 'VIEWER',
      })
    })

    expect(await screen.findByText('updated@example.com · VIEWER')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Delete editor' }))

    await waitFor(() => {
      expect(deleteUserAccountMock).toHaveBeenCalledWith('editor-1')
    })
    await waitFor(() => {
      expect(screen.queryByText('editor')).not.toBeInTheDocument()
    })
  })
})
