import { useEffect, useState } from 'react'

import type { CopyPagePayload, MovePagePayload } from '../types'
import { ModalCard } from './ModalCard'

interface MoveCopyDialogProps {
  mode: 'move' | 'copy'
  open: boolean
  currentPath: string
  onOpenChange: (open: boolean) => void
  onSubmit: (payload: MovePagePayload | CopyPagePayload) => Promise<void>
}

export function MoveCopyDialog({
  mode,
  open,
  currentPath,
  onOpenChange,
  onSubmit,
}: MoveCopyDialogProps) {
  const [targetParentPath, setTargetParentPath] = useState('')
  const [targetSlug, setTargetSlug] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) {
      setTargetParentPath('')
      setTargetSlug('')
    }
  }, [open])

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      await onSubmit({
        targetParentPath,
        targetSlug,
      })
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalCard
      open={open}
      title={mode === 'move' ? 'Move page' : 'Copy page'}
      description={`Current path: /${currentPath || ''}`}
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
            disabled={submitting}
          >
            {submitting ? 'Saving...' : mode === 'move' ? 'Move' : 'Copy'}
          </button>
        </>
      }
    >
      <label className="field">
        <span className="text-sm font-medium">Target parent path</span>
        <input
          className="field-input"
          value={targetParentPath}
          onChange={(event) => setTargetParentPath(event.target.value)}
          placeholder="guides"
        />
      </label>
      <label className="field">
        <span className="text-sm font-medium">Target slug</span>
        <input
          className="field-input"
          value={targetSlug}
          onChange={(event) => setTargetSlug(event.target.value)}
          placeholder="Leave blank to keep the current slug"
        />
      </label>
    </ModalCard>
  )
}
