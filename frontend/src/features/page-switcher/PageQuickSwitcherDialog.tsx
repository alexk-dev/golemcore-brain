import { File, FolderTree } from 'lucide-react'
import { useMemo, useState } from 'react'

import { ModalCard } from '../../components/ModalCard'
import { useTreeStore } from '../../stores/tree'

interface PageQuickSwitcherDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onNavigate: (path: string) => void
}

export function PageQuickSwitcherDialog({
  open,
  onOpenChange,
  onNavigate,
}: PageQuickSwitcherDialogProps) {
  const items = useTreeStore((state) => state.flatPages)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)

  const results = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) {
      return items.slice(0, 20)
    }
    return items
      .filter((item) => `${item.title} ${item.path}`.toLowerCase().includes(normalizedQuery))
      .slice(0, 20)
  }, [items, query])

  const handleSelect = (path: string) => {
    onNavigate(path)
    onOpenChange(false)
  }

  const activeResult = results[Math.min(activeIndex, Math.max(results.length - 1, 0))]

  return (
    <ModalCard
      open={open}
      title="Go to page"
      description="Search existing pages by title or path."
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
              handleSelect(activeResult.path)
            }
          }}
          placeholder="Type a page title…"
        />
      </label>

      <div className="max-h-[60vh] space-y-1 overflow-y-auto">
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
              onClick={() => handleSelect(item.path)}
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
