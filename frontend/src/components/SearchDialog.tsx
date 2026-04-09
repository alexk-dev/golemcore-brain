import { Search, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'

import { searchPages } from '../lib/api'
import type { WikiSearchHit, WikiTreeNode } from '../types'
import { ModalCard } from './ModalCard'

interface SearchDialogProps {
  open: boolean
  tree: WikiTreeNode | null
  onOpenChange: (open: boolean) => void
  onNavigate: (path: string) => void
}

function flattenTree(node: WikiTreeNode | null): WikiSearchHit[] {
  if (!node) {
    return []
  }

  const currentNode: WikiSearchHit = {
    id: node.id,
    path: node.path,
    title: node.title,
    excerpt:
      node.kind === 'ROOT'
        ? 'Root overview and entry page'
        : node.kind === 'SECTION'
          ? 'Section overview and child pages'
          : 'Page in the knowledge base',
    parentPath: node.parentPath,
    kind: node.kind,
  }

  return [currentNode, ...node.children.flatMap((childNode) => flattenTree(childNode))]
}

export function SearchDialog({
  open,
  tree,
  onOpenChange,
  onNavigate,
}: SearchDialogProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<WikiSearchHit[]>([])
  const [loading, setLoading] = useState(false)
  const fallbackItems = useMemo(() => flattenTree(tree), [tree])

  useEffect(() => {
    if (!open) {
      return
    }
    if (!query.trim()) {
      setResults(fallbackItems.slice(0, 12))
      return
    }

    const timeoutId = window.setTimeout(() => {
      setLoading(true)
      searchPages(query)
        .then((response) => setResults(response))
        .finally(() => setLoading(false))
    }, 220)

    return () => window.clearTimeout(timeoutId)
  }, [fallbackItems, open, query])

  const handleSelect = (path: string) => {
    onNavigate(path)
    onOpenChange(false)
  }

  return (
    <ModalCard
      open={open}
      title="Search pages"
      description="Search by page title or markdown content."
      onOpenChange={onOpenChange}
    >
      <label className="field">
        <span className="text-sm font-medium text-foreground">Query</span>
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted" size={16} />
          <input
            autoFocus
            className="field-input w-full pl-9"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search documentation, runbooks, and notes"
          />
        </div>
      </label>

      <div className="space-y-3">
        {loading ? (
          <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-6 text-center text-sm text-muted">
            Searching...
          </div>
        ) : results.length > 0 ? (
          results.map((result) => (
            <button
              type="button"
              key={result.id}
              onClick={() => handleSelect(result.path)}
              className="block w-full rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3 text-left transition hover:-translate-y-0.5 hover:border-accent/40 hover:bg-surface-alt"
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="font-medium text-foreground">{result.title}</div>
                  <div className="mt-1 text-xs uppercase tracking-[0.18em] text-muted">
                    {result.kind}
                  </div>
                </div>
                <div className="rounded-full bg-background px-2 py-1 text-xs text-muted">
                  /{result.path}
                </div>
              </div>
              <p className="mt-3 text-sm text-muted">{result.excerpt}</p>
            </button>
          ))
        ) : (
          <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-6 text-center text-sm text-muted">
            <Sparkles className="mx-auto mb-2 text-muted" size={18} />
            No matching page found.
          </div>
        )}
      </div>
    </ModalCard>
  )
}
