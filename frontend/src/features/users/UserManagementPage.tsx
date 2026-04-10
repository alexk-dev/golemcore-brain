import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'

import { createUser, listUsers } from '../../lib/api'
import { useUiStore } from '../../stores/ui'
import type { PublicUserView, UserRole } from '../../types'

export function UserManagementPage() {
  const currentUser = useUiStore((state) => state.currentUser)
  const [users, setUsers] = useState<PublicUserView[]>([])
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>('EDITOR')

  const loadUsers = async () => {
    try {
      const response = await listUsers()
      setUsers(response)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  useEffect(() => {
    if (currentUser?.role === 'ADMIN') {
      void loadUsers()
    }
  }, [currentUser])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    try {
      await createUser(username, email, password, role)
      toast.success('User created')
      setUsername('')
      setEmail('')
      setPassword('')
      setRole('EDITOR')
      await loadUsers()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  if (currentUser?.role !== 'ADMIN') {
    return <div className="page-editor__error">Admin access is required.</div>
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">User management</h1>
        <form className="mb-6 grid gap-4 md:grid-cols-2" onSubmit={handleSubmit}>
          <label className="field">
            <span className="text-sm font-medium">Username</span>
            <input className="field-input" value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Email</span>
            <input className="field-input" value={email} onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Password</span>
            <input className="field-input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Role</span>
            <select className="field-input" value={role} onChange={(event) => setRole(event.target.value as UserRole)}>
              <option value="ADMIN">Admin</option>
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </label>
          <div className="md:col-span-2">
            <button type="submit" className="action-button-primary">Create user</button>
          </div>
        </form>
        <div className="space-y-3">
          {users.map((user) => (
            <div key={user.id} className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
              <div className="font-medium">{user.username}</div>
              <div className="text-sm text-muted">{user.email} · {user.role}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
