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
