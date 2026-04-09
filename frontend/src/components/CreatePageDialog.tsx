import { useEffect, useMemo, useState } from 'react'

import { inferSlug } from '../lib/paths'
import type { CreatePagePayload, WikiNodeKind } from '../types'
import { ModalCard } from './ModalCard'

interface CreatePageDialogProps {
  open: boolean
  parentPath: string
  kind: Exclude<WikiNodeKind, 'ROOT'>
  onOpenChange: (open: boolean) => void
  onSubmit: (payload: CreatePagePayload) => Promise<void>
}

export function CreatePageDialog({
  open,
  parentPath,
  kind,
  onOpenChange,
  onSubmit,
}: CreatePageDialogProps) {
  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      setTitle('')
      setSlug('')
      setContent('')
    }
  }, [open])

  const defaultSlug = useMemo(() => inferSlug(title), [title])

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      await onSubmit({
        parentPath,
        title,
        slug: slug || defaultSlug,
        content,
        kind,
      })
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalCard
      open={open}
      title={kind === 'SECTION' ? 'Create section' : 'Create page'}
      description={`Parent path: /${parentPath || ''}`}
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
            disabled={!title.trim() || submitting}
          >
            {submitting ? 'Creating...' : 'Create'}
          </button>
        </>
      }
    >
      <label className="field">
        <span className="text-sm font-medium">Title</span>
        <input
          className="field-input"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Customer onboarding"
        />
      </label>
      <label className="field">
        <span className="text-sm font-medium">Slug</span>
        <input
          className="field-input"
          value={slug}
          onChange={(event) => setSlug(event.target.value)}
          placeholder={defaultSlug || 'customer-onboarding'}
        />
      </label>
      <label className="field">
        <span className="text-sm font-medium">Initial content</span>
        <textarea
          className="field-input min-h-32 resize-y"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Write the page body in markdown."
        />
      </label>
    </ModalCard>
  )
}
