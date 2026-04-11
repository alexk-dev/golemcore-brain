import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { toast } from 'sonner'
import { createSpace, deleteSpace } from '../../lib/api'
import { useSpaceStore } from '../../stores/space'

export function SpacesPage() {
  const spaces = useSpaceStore((state) => state.spaces)
  const reloadSpaces = useSpaceStore((state) => state.reloadSpaces)
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    void reloadSpaces().catch((error: Error) => toast.error(error.message))
  }, [reloadSpaces])

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!slug.trim()) {
      toast.error('Slug is required')
      return
    }
    setSubmitting(true)
    try {
      await createSpace(slug.trim(), name.trim())
      toast.success('Space created')
      setSlug('')
      setName('')
      await reloadSpaces()
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (targetSlug: string) => {
    if (!window.confirm(`Delete space "${targetSlug}"? All files in it will remain on disk but become inaccessible.`)) {
      return
    }
    try {
      await deleteSpace(targetSlug)
      toast.success('Space deleted')
      await reloadSpaces()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="shell-form-page">
      <div className="shell-form-page__container">
        <h1 className="shell-form-page__title">Spaces</h1>
        <p className="shell-form-page__subtitle">
          Each space is an isolated workspace of files. Create a space to group related content.
        </p>

        <form className="shell-form" onSubmit={handleCreate}>
          <h2 className="shell-form__heading">Create a new space</h2>
          <label className="shell-form__label">
            Slug
            <input
              className="shell-form__input"
              type="text"
              value={slug}
              placeholder="product-docs"
              onChange={(event) => setSlug(event.target.value)}
              required
              pattern="[a-z0-9][a-z0-9-]{0,62}"
              title="Lowercase letters, numbers, and dashes"
            />
          </label>
          <label className="shell-form__label">
            Name
            <input
              className="shell-form__input"
              type="text"
              value={name}
              placeholder="Product Docs"
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <button className="action-button-primary" type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create space'}
          </button>
        </form>

        <div className="shell-form__section">
          <h2 className="shell-form__heading">Existing spaces</h2>
          {spaces.length === 0 ? (
            <p>No spaces yet.</p>
          ) : (
            <table className="shell-form__table">
              <thead>
                <tr>
                  <th>Slug</th>
                  <th>Name</th>
                  <th>Created</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {spaces.map((space) => (
                  <tr key={space.id}>
                    <td><code>{space.slug}</code></td>
                    <td>{space.name}</td>
                    <td>{new Date(space.createdAt).toLocaleString()}</td>
                    <td>
                      <button
                        type="button"
                        className="action-button-secondary"
                        onClick={() => void handleDelete(space.slug)}
                      >
                        Delete
                      </button>
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
