import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'
import {
  createGlobalApiKey,
  createSpaceApiKey,
  listGlobalApiKeys,
  listSpaceApiKeys,
  revokeApiKey,
} from '../../lib/api'
import { useSpaceStore } from '../../stores/space'
import type { ApiKey, IssuedApiKey, UserRole } from '../../types'

type Scope = 'global' | 'space'

const ROLE_OPTIONS: UserRole[] = ['VIEWER', 'EDITOR', 'ADMIN']

export function ApiKeysPage() {
  const spaces = useSpaceStore((state) => state.spaces)
  const reloadSpaces = useSpaceStore((state) => state.reloadSpaces)
  const activeSlug = useSpaceStore((state) => state.activeSlug)

  const [scope, setScope] = useState<Scope>('global')
  const [spaceSlug, setSpaceSlug] = useState(activeSlug)
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [name, setName] = useState('')
  const [roles, setRoles] = useState<UserRole[]>(['VIEWER'])
  const [expiresAt, setExpiresAt] = useState('')
  const [issued, setIssued] = useState<IssuedApiKey | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    void reloadSpaces().catch((error: Error) => toast.error(error.message))
  }, [reloadSpaces])

  useEffect(() => {
    setSpaceSlug(activeSlug)
  }, [activeSlug])

  const loadKeys = useCallback(async () => {
    try {
      const result = scope === 'global' ? await listGlobalApiKeys() : await listSpaceApiKeys(spaceSlug)
      setKeys(result)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }, [scope, spaceSlug])

  useEffect(() => {
    void loadKeys()
  }, [loadKeys])

  const toggleRole = (role: UserRole) => {
    setRoles((previous) =>
      previous.includes(role) ? previous.filter((value) => value !== role) : [...previous, role],
    )
  }

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!name.trim()) {
      toast.error('Name is required')
      return
    }
    if (roles.length === 0) {
      toast.error('Select at least one role')
      return
    }
    setSubmitting(true)
    try {
      const payload = {
        name: name.trim(),
        roles,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      }
      const response =
        scope === 'global'
          ? await createGlobalApiKey(payload)
          : await createSpaceApiKey(spaceSlug, payload)
      setIssued(response)
      setName('')
      setExpiresAt('')
      await loadKeys()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  const handleRevoke = async (keyId: string) => {
    if (!window.confirm('Revoke this API key? Requests using it will start failing immediately.')) {
      return
    }
    try {
      await revokeApiKey(keyId)
      toast.success('Key revoked')
      await loadKeys()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="shell-form-page">
      <div className="shell-form-page__container">
        <h1 className="shell-form-page__title">API Keys</h1>
        <p className="shell-form-page__subtitle">
          JWT bearer tokens for programmatic access. Global keys work across all spaces; space keys are pinned to one space.
        </p>

        <div className="shell-form__section">
          <div className="shell-form__row">
            <label className="shell-form__label">
              Scope
              <select
                className="shell-form__input"
                value={scope}
                onChange={(event) => setScope(event.target.value as Scope)}
              >
                <option value="global">Global (all spaces)</option>
                <option value="space">Space-scoped</option>
              </select>
            </label>
            {scope === 'space' ? (
              <label className="shell-form__label">
                Space
                <select
                  className="shell-form__input"
                  value={spaceSlug}
                  onChange={(event) => setSpaceSlug(event.target.value)}
                >
                  {spaces.map((space) => (
                    <option key={space.id} value={space.slug}>
                      {space.name} ({space.slug})
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </div>
        </div>

        <form className="shell-form" onSubmit={handleCreate}>
          <h2 className="shell-form__heading">Issue a new key</h2>
          <label className="shell-form__label">
            Name
            <input
              className="shell-form__input"
              type="text"
              value={name}
              placeholder="CI pipeline"
              onChange={(event) => setName(event.target.value)}
              required
            />
          </label>
          <fieldset className="shell-form__label">
            <legend>Roles</legend>
            {ROLE_OPTIONS.map((role) => (
              <label key={role} className="shell-form__checkbox">
                <input
                  type="checkbox"
                  checked={roles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                {role}
              </label>
            ))}
          </fieldset>
          <label className="shell-form__label">
            Expires at (optional)
            <input
              className="shell-form__input"
              type="datetime-local"
              value={expiresAt}
              onChange={(event) => setExpiresAt(event.target.value)}
            />
          </label>
          <button className="action-button-primary" type="submit" disabled={submitting}>
            {submitting ? 'Issuing…' : 'Issue key'}
          </button>
        </form>

        {issued ? (
          <div className="shell-form__section">
            <h2 className="shell-form__heading">Token for "{issued.apiKey.name}"</h2>
            <p>Copy this token now — it will not be shown again.</p>
            <textarea
              readOnly
              className="shell-form__input"
              value={issued.token}
              rows={4}
              onFocus={(event) => event.currentTarget.select()}
            />
            <button className="action-button-secondary" onClick={() => setIssued(null)}>
              Dismiss
            </button>
          </div>
        ) : null}

        <div className="shell-form__section">
          <h2 className="shell-form__heading">{scope === 'global' ? 'Global keys' : `Keys for ${spaceSlug}`}</h2>
          {keys.length === 0 ? (
            <p>No keys yet.</p>
          ) : (
            <table className="shell-form__table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Roles</th>
                  <th>Created</th>
                  <th>Expires</th>
                  <th>Status</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {keys.map((key) => (
                  <tr key={key.id}>
                    <td>{key.name}</td>
                    <td>{key.roles.join(', ')}</td>
                    <td>{new Date(key.createdAt).toLocaleString()}</td>
                    <td>{key.expiresAt ? new Date(key.expiresAt).toLocaleString() : '—'}</td>
                    <td>{key.revoked ? 'Revoked' : 'Active'}</td>
                    <td>
                      {key.revoked ? null : (
                        <button
                          type="button"
                          className="action-button-secondary"
                          onClick={() => void handleRevoke(key.id)}
                        >
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
