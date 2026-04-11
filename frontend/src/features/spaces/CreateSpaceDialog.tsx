import { useEffect, useState } from 'react'

import { ModalCard } from '../../components/ModalCard'

interface CreateSpaceDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (slug: string, name: string) => Promise<void>
}

export function CreateSpaceDialog({ open, onOpenChange, onSubmit }: CreateSpaceDialogProps) {
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      setSlug('')
      setName('')
      setSubmitting(false)
    }
  }, [open])

  const handleSubmit = async () => {
    if (!slug.trim()) return
    setSubmitting(true)
    try {
      await onSubmit(slug.trim(), name.trim())
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalCard
      open={open}
      title="Create space"
      description="Spaces are isolated workspaces of files within this server."
      onOpenChange={onOpenChange}
      footer={
        <>
          <button
            type="button"
            className="action-button-secondary"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </button>
          <button
            type="button"
            className="action-button-primary"
            onClick={handleSubmit}
            disabled={!slug.trim() || submitting}
          >
            {submitting ? 'Creating…' : 'Create space'}
          </button>
        </>
      }
    >
      <label className="field">
        <span className="text-sm font-medium">Slug</span>
        <input
          className="field-input"
          value={slug}
          placeholder="product-docs"
          onChange={(event) => setSlug(event.target.value)}
          pattern="[a-z0-9][a-z0-9-]{0,62}"
          title="Lowercase letters, numbers, and dashes"
        />
      </label>
      <label className="field">
        <span className="text-sm font-medium">Name</span>
        <input
          className="field-input"
          value={name}
          placeholder="Product Docs"
          onChange={(event) => setName(event.target.value)}
        />
      </label>
    </ModalCard>
  )
}
