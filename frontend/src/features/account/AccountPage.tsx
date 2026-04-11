import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { changePassword } from '../../lib/api'
import { useUiStore } from '../../stores/ui'

export function AccountPage() {
  const navigate = useNavigate()
  const setCurrentUser = useUiStore((state) => state.setCurrentUser)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match')
      return
    }
    setSubmitting(true)
    try {
      await changePassword(currentPassword, newPassword)
      setCurrentUser(null)
      toast.success('Password changed. Please sign in again.')
      navigate('/login')
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="shell-form-page">
      <div className="shell-form-page__card surface-card p-6">
        <h1 className="mb-4 text-2xl font-semibold">Change password</h1>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="field">
            <span className="text-sm font-medium">Current password</span>
            <input
              className="field-input"
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">New password</span>
            <input
              className="field-input"
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Confirm password</span>
            <input
              className="field-input"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
          </label>
          <button type="submit" className="action-button-primary w-full" disabled={submitting}>
            {submitting ? 'Changing password...' : 'Change password'}
          </button>
        </form>
      </div>
    </div>
  )
}
