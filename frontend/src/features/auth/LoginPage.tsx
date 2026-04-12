import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { login } from '../../lib/api'
import { useUiStore } from '../../stores/ui'

export function LoginPage() {
  const navigate = useNavigate()
  const setCurrentUser = useUiStore((state) => state.setCurrentUser)
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const response = await login(identifier, password)
      setCurrentUser(response.user)
      toast.success('Logged in')
      navigate('/')
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="shell-form-page">
      <div className="shell-form-page__card surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">Login</h1>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="field">
            <span className="text-sm font-medium">Username or email</span>
            <input
              className="field-input"
              value={identifier}
              placeholder="Enter username or email"
              autoComplete="username"
              onChange={(event) => setIdentifier(event.target.value)}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Password</span>
            <input
              className="field-input"
              type="password"
              value={password}
              placeholder="Enter password"
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          <button type="submit" className="action-button-primary w-full" disabled={submitting}>
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
