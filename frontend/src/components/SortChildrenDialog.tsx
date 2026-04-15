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
import { ArrowDown, ArrowUp } from 'lucide-react'

import type { WikiTreeNode } from '../types'
import { ModalCard } from './ModalCard'

interface SortChildrenDialogProps {
  open: boolean
  page: WikiTreeNode | null
  onOpenChange: (open: boolean) => void
  onSubmit: (orderedSlugs: string[]) => Promise<void>
}

export function SortChildrenDialog({
  open,
  page,
  onOpenChange,
  onSubmit,
}: SortChildrenDialogProps) {
  const [items, setItems] = useState<WikiTreeNode[]>([])
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !page) {
      setItems([])
      return
    }
    setItems(page.children)
  }, [open, page])

  const moveItem = (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction
    if (targetIndex < 0 || targetIndex >= items.length) {
      return
    }
    const nextItems = [...items]
    const [currentItem] = nextItems.splice(index, 1)
    nextItems.splice(targetIndex, 0, currentItem)
    setItems(nextItems)
  }

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      await onSubmit(items.map((item) => item.slug))
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalCard
      open={open}
      title="Sort child pages"
      description={page ? `Section: /${page.path}` : 'Select a section first.'}
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
            disabled={submitting || items.length === 0}
          >
            {submitting ? 'Saving...' : 'Save order'}
          </button>
        </>
      }
    >
      {items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-6 text-center text-sm text-muted">
          This section has no child pages to reorder.
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item, index) => (
            <div
              key={item.path || item.slug}
              className="flex items-center justify-between gap-3 rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3"
            >
              <div>
                <div className="text-xs uppercase tracking-[0.18em] text-muted">
                  {item.kind}
                </div>
                <div className="mt-1 text-sm font-medium text-foreground">{item.title}</div>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => moveItem(index, -1)}
                  disabled={index === 0}
                >
                  <ArrowUp size={16} />
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => moveItem(index, 1)}
                  disabled={index === items.length - 1}
                >
                  <ArrowDown size={16} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </ModalCard>
  )
}
