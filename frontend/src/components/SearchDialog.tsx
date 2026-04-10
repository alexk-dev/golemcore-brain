import { Search, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

import { searchPages } from '../lib/api'
import type { WikiSearchHit, WikiTreeNode } from '../types'
import { ModalCard } from './ModalCard'

interface SearchDialogProps {
  open: boolean
  tree: WikiTreeNode | null
  onOpenChange: (open: boolean) => void
  onNavigate: (path: string) => void
}

const SEARCH_PAGE_SIZE = 10

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
  const [pageIndex, setPageIndex] = useState(0)
  const [activeIndex, setActiveIndex] = useState(0)
  const resultRefs = useRef<(HTMLButtonElement | null)[]>([])
  const fallbackItems = useMemo(() => flattenTree(tree), [tree])

  const visibleResults = useMemo(() => {
    const start = pageIndex * SEARCH_PAGE_SIZE
    return results.slice(start, start + SEARCH_PAGE_SIZE)
  }, [pageIndex, results])

  useEffect(() => {
    if (!open) {
      setQuery('')
      setResults([])
      setPageIndex(0)
      setActiveIndex(0)
      return
    }
    if (!query.trim()) {
      setResults(fallbackItems.slice(0, 24))
      setPageIndex(0)
      setActiveIndex(0)
      return
    }

    const timeoutId = window.setTimeout(() => {
      setLoading(true)
      searchPages(query)
        .then((response) => {
          setResults(response)
          setPageIndex(0)
          setActiveIndex(0)
        })
        .finally(() => setLoading(false))
    }, 220)

    return () => window.clearTimeout(timeoutId)
  }, [fallbackItems, open, query])

  useEffect(() => {
    resultRefs.current = resultRefs.current.slice(0, visibleResults.length)
  }, [visibleResults])

  useEffect(() => {
    if (visibleResults.length === 0) {
      return
    }
    resultRefs.current[Math.min(activeIndex, visibleResults.length - 1)]?.scrollIntoView({
      block: 'nearest',
    })
  }, [activeIndex, visibleResults])

  const handleSelect = (path: string) => {
    onNavigate(path)
    onOpenChange(false)
  }

  const handleMoveSelection = (direction: -1 | 1) => {
    setActiveIndex((current) => {
      const nextIndex = current + direction
      if (nextIndex < 0) {
        if (pageIndex > 0) {
          setPageIndex((currentPage) => currentPage - 1)
          return SEARCH_PAGE_SIZE - 1
        }
        return 0
      }
      if (nextIndex >= visibleResults.length) {
        const nextPageExists = (pageIndex + 1) * SEARCH_PAGE_SIZE < results.length
        if (nextPageExists) {
          setPageIndex((currentPage) => currentPage + 1)
          return 0
        }
        return visibleResults.length - 1
      }
      return nextIndex
    })
  }

  const activeResult = visibleResults[Math.min(activeIndex, Math.max(visibleResults.length - 1, 0))]
  const canGoPrevious = pageIndex > 0
  const canGoNext = (pageIndex + 1) * SEARCH_PAGE_SIZE < results.length

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
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown') {
                event.preventDefault()
                handleMoveSelection(1)
              }
              if (event.key === 'ArrowUp') {
                event.preventDefault()
                handleMoveSelection(-1)
              }
              if (event.key === 'Enter' && activeResult) {
                event.preventDefault()
                handleSelect(activeResult.path)
              }
            }}
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
          <>
            <div className="text-sm text-muted">
              Showing {Math.min(results.length, pageIndex * SEARCH_PAGE_SIZE + 1)}-
              {Math.min(results.length, pageIndex * SEARCH_PAGE_SIZE + visibleResults.length)} of {results.length} result(s)
            </div>
            {visibleResults.map((result, index) => {
              const isActive = index === activeIndex
              return (
                <button
                  type="button"
                  key={result.id}
                  ref={(element) => {
                    resultRefs.current[index] = element
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => handleSelect(result.path)}
                  className={[
                    'block w-full rounded-2xl border px-4 py-3 text-left transition',
                    isActive
                      ? 'border-accent/50 bg-surface-alt shadow-sm'
                      : 'border-surface-border bg-surface-alt/60 hover:-translate-y-0.5 hover:border-accent/40 hover:bg-surface-alt',
                  ].join(' ')}
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
              )
            })}
            {results.length > SEARCH_PAGE_SIZE ? (
              <div className="flex justify-between gap-3 pt-2">
                <button
                  type="button"
                  className="action-button-secondary"
                  disabled={!canGoPrevious}
                  onClick={() => {
                    setPageIndex((current) => Math.max(current - 1, 0))
                    setActiveIndex(0)
                  }}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  disabled={!canGoNext}
                  onClick={() => {
                    setPageIndex((current) => current + 1)
                    setActiveIndex(0)
                  }}
                >
                  Next
                </button>
              </div>
            ) : null}
          </>
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
