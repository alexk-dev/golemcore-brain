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
