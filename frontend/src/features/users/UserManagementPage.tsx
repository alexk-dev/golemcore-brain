import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'

import { createUser, deleteUserAccount, listUsers, updateUser } from '../../lib/api'
import { useUiStore } from '../../stores/ui'
import type { PublicUserView, UserRole } from '../../types'

interface UserFormState {
  username: string
  email: string
  password: string
  role: UserRole
}

const EMPTY_FORM_STATE: UserFormState = {
  username: '',
  email: '',
  password: '',
  role: 'EDITOR',
}

export function UserManagementPage() {
  const currentUser = useUiStore((state) => state.currentUser)
  const [users, setUsers] = useState<PublicUserView[]>([])
  const [createForm, setCreateForm] = useState<UserFormState>(EMPTY_FORM_STATE)
  const [editingUserId, setEditingUserId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<UserFormState>(EMPTY_FORM_STATE)

  const isAdmin = currentUser?.role === 'ADMIN'

  const loadUsers = async () => {
    try {
      const response = await listUsers()
      setUsers(response)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  useEffect(() => {
    if (isAdmin) {
      void loadUsers()
    }
  }, [isAdmin])

  const editingUser = useMemo(
    () => users.find((user) => user.id === editingUserId) ?? null,
    [editingUserId, users],
  )

  const handleCreateSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    try {
      await createUser(createForm.username, createForm.email, createForm.password, createForm.role)
      toast.success('User created')
      setCreateForm(EMPTY_FORM_STATE)
      await loadUsers()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleEditStart = (user: PublicUserView) => {
    setEditingUserId(user.id)
    setEditForm({
      username: user.username,
      email: user.email,
      password: '',
      role: user.role,
    })
  }

  const handleEditSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editingUserId) {
      return
    }
    try {
      await updateUser(editingUserId, editForm)
      toast.success('User updated')
      setEditingUserId(null)
      setEditForm(EMPTY_FORM_STATE)
      await loadUsers()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const handleDelete = async (user: PublicUserView) => {
    const confirmed = window.confirm(`Delete user ${user.username}?`)
    if (!confirmed) {
      return
    }
    try {
      await deleteUserAccount(user.id)
      toast.success('User deleted')
      if (editingUserId === user.id) {
        setEditingUserId(null)
        setEditForm(EMPTY_FORM_STATE)
      }
      await loadUsers()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  if (!isAdmin) {
    return (
      <div className="shell-form-page">
        <div className="shell-form-page__card--wide surface-card p-6">
          <h2 className="mb-2 text-xl font-semibold">Admin access required</h2>
          <p className="text-sm text-muted">You must be signed in as an administrator to manage users.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">User management</h1>
        <form className="mb-6 grid gap-4 md:grid-cols-2" onSubmit={handleCreateSubmit}>
          <label className="field">
            <span className="text-sm font-medium">Username</span>
            <input
              className="field-input"
              value={createForm.username}
              onChange={(event) => setCreateForm((state) => ({ ...state, username: event.target.value }))}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Email</span>
            <input
              className="field-input"
              value={createForm.email}
              onChange={(event) => setCreateForm((state) => ({ ...state, email: event.target.value }))}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Password</span>
            <input
              className="field-input"
              type="password"
              value={createForm.password}
              onChange={(event) => setCreateForm((state) => ({ ...state, password: event.target.value }))}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Role</span>
            <select
              className="field-input"
              value={createForm.role}
              onChange={(event) => setCreateForm((state) => ({ ...state, role: event.target.value as UserRole }))}
            >
              <option value="ADMIN">Admin</option>
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </label>
          <div className="md:col-span-2">
            <button type="submit" className="action-button-primary">Create user</button>
          </div>
        </form>

        {editingUser ? (
          <form className="mb-6 grid gap-4 rounded-2xl border border-surface-border p-4 md:grid-cols-2" onSubmit={handleEditSubmit}>
            <div className="md:col-span-2 text-lg font-medium">Edit {editingUser.username}</div>
            <label className="field">
              <span className="text-sm font-medium">Username</span>
              <input
                className="field-input"
                value={editForm.username}
                onChange={(event) => setEditForm((state) => ({ ...state, username: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Email</span>
              <input
                className="field-input"
                value={editForm.email}
                onChange={(event) => setEditForm((state) => ({ ...state, email: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Password</span>
              <input
                className="field-input"
                type="password"
                value={editForm.password}
                placeholder="Leave blank to keep current password"
                onChange={(event) => setEditForm((state) => ({ ...state, password: event.target.value }))}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Role</span>
              <select
                className="field-input"
                value={editForm.role}
                onChange={(event) => setEditForm((state) => ({ ...state, role: event.target.value as UserRole }))}
              >
                <option value="ADMIN">Admin</option>
                <option value="EDITOR">Editor</option>
                <option value="VIEWER">Viewer</option>
              </select>
            </label>
            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className="action-button-primary">Save changes</button>
              <button
                type="button"
                className="action-button-secondary"
                onClick={() => {
                  setEditingUserId(null)
                  setEditForm(EMPTY_FORM_STATE)
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        ) : null}

        <div className="space-y-3">
          {users.map((user) => (
            <div key={user.id} className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="font-medium">{user.username}</div>
                  <div className="text-sm text-muted">{user.email} · {user.role}</div>
                </div>
                <div className="flex gap-2">
                  <button type="button" className="action-button-secondary" onClick={() => handleEditStart(user)}>
                    Edit {user.username}
                  </button>
                  <button type="button" className="action-button-secondary" onClick={() => void handleDelete(user)}>
                    Delete {user.username}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
