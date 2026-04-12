import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { createSpace, deleteSpace } from '../../lib/api'
import { useSpaceStore } from '../../stores/space'
import { useUiStore } from '../../stores/ui'
import { CreateSpaceDialog } from './CreateSpaceDialog'
import { DeleteSpaceDialog } from './DeleteSpaceDialog'

export function SpacesPage() {
  const currentUser = useUiStore((state) => state.currentUser)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const isAdmin = authDisabled || currentUser?.role === 'ADMIN'

  const spaces = useSpaceStore((state) => state.spaces)
  const reloadSpaces = useSpaceStore((state) => state.reloadSpaces)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)

  useEffect(() => {
    if (isAdmin) {
      void reloadSpaces().catch((error: Error) => toast.error(error.message))
    }
  }, [isAdmin, reloadSpaces])

  if (!isAdmin) {
    return (
      <div className="shell-form-page">
        <div className="shell-form-page__card--wide surface-card p-6">
          <h2 className="mb-2 text-xl font-semibold">Admin access required</h2>
          <p className="text-sm text-muted">You must be signed in as an administrator to manage spaces.</p>
        </div>
      </div>
    )
  }

  const handleCreate = async (slug: string, name: string) => {
    try {
      await createSpace(slug, name)
      toast.success('Space created')
      await reloadSpaces()
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await deleteSpace(deleteTarget)
      toast.success('Space deleted')
      await reloadSpaces()
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div className="page-viewer">
      <div className="surface-card p-6">
        <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="mb-1 text-2xl font-semibold">Spaces</h1>
            <p className="text-sm text-muted">
              Each space is an isolated workspace of files. Create a space to group related content.
            </p>
          </div>
          <button
            type="button"
            className="action-button-primary"
            onClick={() => setCreateOpen(true)}
          >
            Create space
          </button>
        </div>

        <div className="mb-3 text-lg font-medium">Existing spaces</div>
        {spaces.length === 0 ? (
          <div className="text-sm text-muted">No spaces yet.</div>
        ) : (
          <div className="space-y-3">
            {spaces.map((space) => (
              <div
                key={space.id}
                className="rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="font-medium">{space.name || space.slug}</div>
                    <div className="text-sm text-muted">
                      <code>{space.slug}</code> · created {new Date(space.createdAt).toLocaleString()}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Link className="action-button-secondary" to={'/spaces/' + encodeURIComponent(space.slug) + '/settings'}>
                      Settings
                    </Link>
                    <button
                      type="button"
                      className="action-button-secondary"
                      onClick={() => setDeleteTarget(space.slug)}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <CreateSpaceDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={handleCreate}
      />
      <DeleteSpaceDialog
        open={deleteTarget !== null}
        slug={deleteTarget ?? ''}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
        onConfirm={handleDelete}
      />
    </div>
  )
}
