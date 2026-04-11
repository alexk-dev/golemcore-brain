import { File, FolderTree } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

import { ModalCard } from './ModalCard'
import { useTreeStore } from '../stores/tree'
import type { WikiTreeNode } from '../types'

interface InsertWikiLinkDialogProps {
  open: boolean
  initialQuery?: string
  onOpenChange: (open: boolean) => void
  onSelect: (page: Pick<WikiTreeNode, 'path' | 'title'>) => void
}

export function InsertWikiLinkDialog({
  open,
  initialQuery = '',
  onOpenChange,
  onSelect,
}: InsertWikiLinkDialogProps) {
  const items = useTreeStore((state) => state.flatPages)
  const [query, setQuery] = useState(initialQuery)
  const [activeIndex, setActiveIndex] = useState(0)

  useEffect(() => {
    if (open) {
      setQuery(initialQuery)
      setActiveIndex(0)
    }
  }, [initialQuery, open])

  const results = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) {
      return items.slice(0, 20)
    }
    return items
      .filter((item) => `${item.title} ${item.path}`.toLowerCase().includes(normalizedQuery))
      .slice(0, 20)
  }, [items, query])

  const activeResult = results[Math.min(activeIndex, Math.max(results.length - 1, 0))]

  const handleSelect = (page: Pick<WikiTreeNode, 'path' | 'title'>) => {
    onSelect(page)
    onOpenChange(false)
  }

  return (
    <ModalCard
      open={open}
      title="Insert wiki link"
      description="Pick a page to link to. The selected text becomes the link label."
      onOpenChange={onOpenChange}
    >
      <label className="field">
        <span className="text-sm font-medium">Search pages</span>
        <input
          className="field-input"
          autoFocus
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActiveIndex(0)
          }}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault()
              setActiveIndex((current) => Math.min(current + 1, Math.max(results.length - 1, 0)))
            }
            if (event.key === 'ArrowUp') {
              event.preventDefault()
              setActiveIndex((current) => Math.max(current - 1, 0))
            }
            if (event.key === 'Enter' && activeResult) {
              event.preventDefault()
              handleSelect(activeResult)
            }
          }}
          placeholder="Type a page title or path…"
        />
      </label>

      <div className="max-h-[60vh] space-y-1 overflow-y-auto" data-testid="insert-wiki-link-results">
        {results.length === 0 ? (
          <div className="text-sm text-muted">No matching page found.</div>
        ) : (
          results.map((item, index) => (
            <button
              type="button"
              key={item.id}
              className={[
                'flex w-full items-start gap-3 rounded-md px-3 py-2 text-left transition',
                index === activeIndex ? 'bg-surface-alt border border-accent/30' : 'hover:bg-surface-alt',
              ].join(' ')}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => handleSelect(item)}
            >
              {item.kind === 'SECTION' ? <FolderTree size={16} /> : <File size={16} />}
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{item.title}</span>
                <span className="block truncate text-xs text-muted">/{item.path}</span>
              </span>
            </button>
          ))
        )}
      </div>
    </ModalCard>
  )
}
