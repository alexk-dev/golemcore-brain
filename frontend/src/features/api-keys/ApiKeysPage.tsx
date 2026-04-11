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
import { useUiStore } from '../../stores/ui'
import type { ApiKey, IssuedApiKey, UserRole } from '../../types'

type Scope = 'global' | 'space'

const ROLE_OPTIONS: UserRole[] = ['VIEWER', 'EDITOR', 'ADMIN']

export function ApiKeysPage() {
  const currentUser = useUiStore((state) => state.currentUser)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const isAdmin = authDisabled || currentUser?.role === 'ADMIN'

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
    if (isAdmin) {
      void reloadSpaces().catch((error: Error) => toast.error(error.message))
    }
  }, [isAdmin, reloadSpaces])

  useEffect(() => {
    setSpaceSlug(activeSlug)
  }, [activeSlug])

  const loadKeys = useCallback(async () => {
    if (!isAdmin) return
    try {
      const result = scope === 'global' ? await listGlobalApiKeys() : await listSpaceApiKeys(spaceSlug)
      setKeys(result)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }, [isAdmin, scope, spaceSlug])

  useEffect(() => {
    void loadKeys()
  }, [loadKeys])

  if (!isAdmin) {
    return (
      <div className="shell-form-page">
        <div className="shell-form-page__card--wide surface-card p-6">
          <h2 className="mb-2 text-xl font-semibold">Admin access required</h2>
          <p className="text-sm text-muted">You must be signed in as an administrator to manage API keys.</p>
        </div>
      </div>
    )
  }

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
    try {
      await revokeApiKey(keyId)
      toast.success('Key revoked')
      await loadKeys()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <h1 className="mb-1 text-2xl font-semibold">API Keys</h1>
        <p className="mb-6 text-sm text-muted">
          JWT bearer tokens for programmatic access. Global keys work across all spaces; space keys are pinned to one space.
        </p>

        <div className="mb-6 grid gap-4 md:grid-cols-2">
          <label className="field">
            <span className="text-sm font-medium">Scope</span>
            <select
              className="field-input"
              value={scope}
              onChange={(event) => setScope(event.target.value as Scope)}
            >
              <option value="global">Global (all spaces)</option>
              <option value="space">Space-scoped</option>
            </select>
          </label>
          {scope === 'space' ? (
            <label className="field">
              <span className="text-sm font-medium">Space</span>
              <select
                className="field-input"
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

        <form className="mb-6 grid gap-4 md:grid-cols-2" onSubmit={handleCreate}>
          <div className="md:col-span-2 text-lg font-medium">Issue a new key</div>
          <label className="field">
            <span className="text-sm font-medium">Name</span>
            <input
              className="field-input"
              type="text"
              value={name}
              placeholder="CI pipeline"
              onChange={(event) => setName(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span className="text-sm font-medium">Expires at (optional)</span>
            <input
              className="field-input"
              type="datetime-local"
              value={expiresAt}
              onChange={(event) => setExpiresAt(event.target.value)}
            />
          </label>
          <div className="md:col-span-2">
            <div className="text-sm font-medium">Roles</div>
            <div className="mt-2 flex flex-wrap gap-4">
              {ROLE_OPTIONS.map((role) => (
                <label key={role} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={roles.includes(role)}
                    onChange={() => toggleRole(role)}
                  />
                  {role}
                </label>
              ))}
            </div>
          </div>
          <div className="md:col-span-2">
            <button className="action-button-primary" type="submit" disabled={submitting}>
              {submitting ? 'Issuing…' : 'Issue key'}
            </button>
          </div>
        </form>

        {issued ? (
          <div className="mb-6 rounded-2xl border border-accent/50 bg-accent/5 p-4">
            <div className="mb-1 text-sm font-semibold">Token for "{issued.apiKey.name}"</div>
            <p className="mb-2 text-sm text-muted">Copy this token now — it will not be shown again.</p>
            <textarea
              readOnly
              className="field-input w-full font-mono text-xs"
              value={issued.token}
              rows={4}
              onFocus={(event) => event.currentTarget.select()}
            />
            <div className="mt-3">
              <button className="action-button-secondary" type="button" onClick={() => setIssued(null)}>
                Dismiss
              </button>
            </div>
          </div>
        ) : null}

        <div className="mb-3 text-lg font-medium">
          {scope === 'global' ? 'Global keys' : `Keys for ${spaceSlug}`}
        </div>
        {keys.length === 0 ? (
          <div className="text-sm text-muted">No keys yet.</div>
        ) : (
          <div className="space-y-3">
            {keys.map((key) => (
              <div
                key={key.id}
                className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="font-medium">{key.name}</div>
                    <div className="text-sm text-muted">
                      <span>{key.roles.join(', ')}</span>
                      <span> · created {new Date(key.createdAt).toLocaleString()}</span>
                      <span> · expires {key.expiresAt ? new Date(key.expiresAt).toLocaleString() : '—'}</span>
                      <span> · {key.revoked ? 'Revoked' : 'Active'}</span>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    {key.revoked ? null : (
                      <button
                        type="button"
                        className="action-button-secondary"
                        onClick={() => void handleRevoke(key.id)}
                      >
                        Revoke
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
