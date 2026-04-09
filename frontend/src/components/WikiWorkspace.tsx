import {
  ArrowDownUp,
  Copy,
  FolderPlus,
  Menu,
  Moon,
  MoveRight,
  Pencil,
  Plus,
  Search,
  Sun,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { toast } from 'sonner'

import {
  copyPage,
  createPage,
  deletePage,
  getConfig,
  getPage,
  getTree,
  movePage,
  sortSection,
  updatePage,
} from '../lib/api'
import { inferSlug, normalizeWikiPath, parentPath } from '../lib/paths'
import type {
  CopyPagePayload,
  CreatePagePayload,
  MovePagePayload,
  UpdatePagePayload,
  WikiConfig,
  WikiPage,
  WikiTreeNode,
} from '../types'
import { CreatePageDialog } from './CreatePageDialog'
import { DeleteDialog } from './DeleteDialog'
import { MoveCopyDialog } from './MoveCopyDialog'
import { SearchDialog } from './SearchDialog'
import { SortChildrenDialog } from './SortChildrenDialog'
import { TreeNodeItem } from './TreeNodeItem'

type DialogState =
  | { type: 'none' }
  | { type: 'create'; parentPath: string; kind: 'PAGE' | 'SECTION' }
  | { type: 'move' }
  | { type: 'copy' }
  | { type: 'delete' }
  | { type: 'sort' }

function collectOpenPaths(targetPath: string): string[] {
  if (!targetPath) {
    return ['']
  }

  const segments = targetPath.split('/').filter(Boolean)
  const openPaths = ['']
  let currentPath = ''
  segments.slice(0, -1).forEach((segment) => {
    currentPath = currentPath ? `${currentPath}/${segment}` : segment
    openPaths.push(currentPath)
  })
  return openPaths
}

function buildBreadcrumbs(path: string, title: string): Array<{ label: string; path: string }> {
  const normalizedPath = normalizeWikiPath(path)
  if (!normalizedPath) {
    return [{ label: title, path: '' }]
  }

  const segments = normalizedPath.split('/')
  const breadcrumbs = [{ label: 'Home', path: '' }]
  let currentPath = ''
  segments.forEach((segment, index) => {
    currentPath = currentPath ? `${currentPath}/${segment}` : segment
    breadcrumbs.push({
      label:
        index === segments.length - 1
          ? title
          : segment.replace(/-/g, ' ').replace(/\b\w/g, (value) => value.toUpperCase()),
      path: currentPath,
    })
  })
  return breadcrumbs
}

export function WikiWorkspace() {
  const [config, setConfig] = useState<WikiConfig | null>(null)
  const [tree, setTree] = useState<WikiTreeNode | null>(null)
  const [page, setPage] = useState<WikiPage | null>(null)
  const [editing, setEditing] = useState(false)
  const [draftTitle, setDraftTitle] = useState('')
  const [draftSlug, setDraftSlug] = useState('')
  const [draftContent, setDraftContent] = useState('')
  const [activePath, setActivePath] = useState('')
  const [openPaths, setOpenPaths] = useState<string[]>([''])
  const [searchOpen, setSearchOpen] = useState(false)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [dialogState, setDialogState] = useState<DialogState>({ type: 'none' })
  const [isDark, setIsDark] = useState<boolean>(() =>
    window.matchMedia('(prefers-color-scheme: dark)').matches,
  )
  const [isLoading, setIsLoading] = useState(true)

  const syncBrowserPath = useCallback((path: string) => {
    const normalizedPath = normalizeWikiPath(path)
    const nextUrl = normalizedPath ? `/${normalizedPath}` : '/'
    if (window.location.pathname !== nextUrl) {
      window.history.pushState({}, '', nextUrl)
    }
  }, [])

  const loadPage = useCallback(
    async (path: string) => {
      setIsLoading(true)
      try {
        const [nextConfig, nextTree, nextPage] = await Promise.all([
          config ? Promise.resolve(config) : getConfig(),
          getTree(),
          getPage(path),
        ])
        setConfig(nextConfig)
        setTree(nextTree)
        setPage(nextPage)
        setDraftTitle(nextPage.title)
        setDraftSlug(nextPage.slug)
        setDraftContent(nextPage.content)
        setActivePath(nextPage.path)
        setOpenPaths((currentOpenPaths) =>
          Array.from(new Set([...currentOpenPaths, ...collectOpenPaths(nextPage.path)])),
        )
        setEditing(false)
        syncBrowserPath(nextPage.path)
        document.title = `${nextPage.title} · ${nextConfig.siteTitle}`
      } finally {
        setIsLoading(false)
      }
    },
    [config, syncBrowserPath],
  )

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark)
  }, [isDark])

  useEffect(() => {
    const currentPath = normalizeWikiPath(window.location.pathname)
    void loadPage(currentPath).catch((error: Error) => {
      toast.error(error.message)
      setIsLoading(false)
    })

    const handlePopState = () => {
      const nextPath = normalizeWikiPath(window.location.pathname)
      void loadPage(nextPath).catch((error: Error) => toast.error(error.message))
    }

    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [loadPage])

  const handleSave = useCallback(async () => {
    if (!page) {
      return
    }
    const payload: UpdatePagePayload = {
      title: draftTitle,
      slug: draftSlug || inferSlug(draftTitle),
      content: draftContent,
    }
    try {
      const updatedPage = await updatePage(page.path, payload)
      toast.success('Page saved')
      await loadPage(updatedPage.path)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }, [draftContent, draftSlug, draftTitle, loadPage, page])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const modifierPressed = event.metaKey || event.ctrlKey
      if (modifierPressed && event.key.toLowerCase() === 's' && editing) {
        event.preventDefault()
        void handleSave()
      }
      if (modifierPressed && event.shiftKey && event.key.toLowerCase() === 'f') {
        event.preventDefault()
        setSearchOpen(true)
      }
      if (modifierPressed && event.key.toLowerCase() === 'e' && page) {
        event.preventDefault()
        setEditing(true)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [editing, handleSave, page])

  const handleNavigate = useCallback(
    (path: string) => {
      const normalizedPath = normalizeWikiPath(path)
      setMobileSidebarOpen(false)
      void loadPage(normalizedPath).catch((error: Error) => toast.error(error.message))
    },
    [loadPage],
  )

  const handleToggle = (path: string) => {
    setOpenPaths((currentOpenPaths) =>
      currentOpenPaths.includes(path)
        ? currentOpenPaths.filter((entry) => entry !== path)
        : [...currentOpenPaths, path],
    )
  }

  const handleCreate = async (payload: CreatePagePayload) => {
    try {
      const createdPage = await createPage(payload)
      toast.success(payload.kind === 'SECTION' ? 'Section created' : 'Page created')
      await loadPage(createdPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleMoveCopy = async (
    mode: 'move' | 'copy',
    payload: MovePagePayload | CopyPagePayload,
  ) => {
    if (!page) {
      return
    }
    try {
      const nextPage =
        mode === 'move'
          ? await movePage(page.path, payload as MovePagePayload)
          : await copyPage(page.path, payload as CopyPagePayload)
      toast.success(mode === 'move' ? 'Page moved' : 'Page copied')
      await loadPage(nextPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleDelete = async () => {
    if (!page) {
      return
    }
    try {
      const nextPath = parentPath(page.path)
      await deletePage(page.path)
      toast.success('Page deleted')
      await loadPage(nextPath)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleSort = async (orderedSlugs: string[]) => {
    if (!page || page.kind === 'PAGE') {
      return
    }
    try {
      await sortSection(page.path, orderedSlugs)
      toast.success('Section order saved')
      await loadPage(page.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const isDirty = useMemo(() => {
    if (!page) {
      return false
    }
    return (
      draftTitle !== page.title ||
      (draftSlug || page.slug) !== page.slug ||
      draftContent !== page.content
    )
  }, [draftContent, draftSlug, draftTitle, page])

  const breadcrumbs = useMemo(() => {
    if (!page) {
      return []
    }
    return buildBreadcrumbs(page.path, page.title)
  }, [page])

  const createTargetParentPath = page?.kind === 'SECTION' ? page.path : page?.parentPath ?? ''

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="flex min-h-screen">
        <aside
          className={[
            'fixed inset-y-0 left-0 z-30 w-80 border-r border-surface-border bg-sidebar px-4 py-5 text-sidebar-foreground transition md:static md:translate-x-0',
            mobileSidebarOpen ? 'translate-x-0' : '-translate-x-full',
          ].join(' ')}
        >
          <div className="flex items-center justify-between gap-3 px-2">
            <div>
              <p className="text-xs uppercase tracking-[0.28em] text-sidebar-foreground/50">
                Workspace
              </p>
              <h1 className="mt-2 text-2xl font-semibold">{config?.siteTitle ?? 'Loading...'}</h1>
            </div>
            <button
              type="button"
              className="rounded-xl border border-white/10 p-2 text-sidebar-foreground/70 transition hover:bg-white/10 md:hidden"
              onClick={() => setMobileSidebarOpen(false)}
            >
              <Menu size={18} />
            </button>
          </div>

          <div className="mt-6 flex gap-2 px-2">
            <button
              type="button"
              className="action-button-primary flex-1"
              onClick={() =>
                setDialogState({
                  type: 'create',
                  parentPath: createTargetParentPath,
                  kind: 'PAGE',
                })
              }
            >
              <Plus size={16} />
              New page
            </button>
            <button
              type="button"
              className="action-button-secondary border-white/10 bg-white/5 text-sidebar-foreground hover:bg-white/10"
              onClick={() =>
                setDialogState({
                  type: 'create',
                  parentPath: createTargetParentPath,
                  kind: 'SECTION',
                })
              }
            >
              <FolderPlus size={16} />
            </button>
          </div>

          <button
            type="button"
            className="mt-4 flex w-full items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-left text-sm text-sidebar-foreground/70 transition hover:bg-white/10"
            onClick={() => setSearchOpen(true)}
          >
            <Search size={16} />
            Search markdown knowledge
          </button>

          <div className="mt-6 max-h-[calc(100vh-14rem)] overflow-y-auto pr-1">
            <ul className="space-y-1">
              {tree?.children.map((childNode) => (
                <TreeNodeItem
                  key={childNode.path || childNode.slug}
                  node={childNode}
                  activePath={activePath}
                  openPaths={openPaths}
                  onNavigate={handleNavigate}
                  onToggle={handleToggle}
                  onCreate={(parentPathValue, kind) =>
                    setDialogState({ type: 'create', parentPath: parentPathValue, kind })
                  }
                />
              ))}
            </ul>
          </div>
        </aside>

        <main className="flex min-h-screen flex-1 flex-col">
          <header className="sticky top-0 z-20 border-b border-surface-border bg-background/85 px-4 py-4 backdrop-blur md:px-8">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  className="rounded-xl border border-surface-border p-2 transition hover:bg-surface-alt md:hidden"
                  onClick={() => setMobileSidebarOpen(true)}
                >
                  <Menu size={18} />
                </button>
                <div>
                  <nav className="flex flex-wrap items-center gap-2 text-sm text-muted">
                    {breadcrumbs.map((breadcrumb, index) => (
                      <div key={breadcrumb.path || 'root'} className="flex items-center gap-2">
                        <button
                          type="button"
                          className="transition hover:text-foreground"
                          onClick={() => handleNavigate(breadcrumb.path)}
                        >
                          {breadcrumb.label}
                        </button>
                        {index < breadcrumbs.length - 1 ? <span>/</span> : null}
                      </div>
                    ))}
                  </nav>
                  <div className="mt-2 flex items-center gap-3">
                    <h2 className="text-2xl font-semibold tracking-tight md:text-3xl">
                      {page?.title ?? 'Loading page...'}
                    </h2>
                    {isDirty ? (
                      <span className="rounded-full bg-warning/15 px-2.5 py-1 text-xs font-medium text-warning">
                        Unsaved
                      </span>
                    ) : null}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setIsDark((currentValue) => !currentValue)}
                  title="Toggle theme"
                >
                  {isDark ? <Sun size={16} /> : <Moon size={16} />}
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setSearchOpen(true)}
                >
                  <Search size={16} />
                  Search
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setEditing((currentValue) => !currentValue)}
                >
                  <Pencil size={16} />
                  {editing ? 'Preview' : 'Edit'}
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setDialogState({ type: 'sort' })}
                  disabled={!page || page.kind === 'PAGE' || page.children.length < 2}
                >
                  <ArrowDownUp size={16} />
                  Sort
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setDialogState({ type: 'copy' })}
                  disabled={!page}
                >
                  <Copy size={16} />
                  Copy
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={() => setDialogState({ type: 'move' })}
                  disabled={!page}
                >
                  <MoveRight size={16} />
                  Move
                </button>
                <button
                  type="button"
                  className="action-button-danger"
                  onClick={() => setDialogState({ type: 'delete' })}
                  disabled={!page || page.path === ''}
                >
                  <Trash2 size={16} />
                  Delete
                </button>
              </div>
            </div>
          </header>

          <div className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col gap-6 px-4 py-6 md:px-8 lg:grid lg:grid-cols-[minmax(0,0.95fr)_minmax(380px,0.85fr)]">
            <section className="surface-card flex flex-col overflow-hidden">
              <div className="border-b border-surface-border bg-surface-alt/70 px-5 py-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-foreground">Reading view</p>
                    <p className="mt-1 text-sm text-muted">
                      Markdown rendered from the filesystem-backed knowledge base.
                    </p>
                  </div>
                  {page ? (
                    <div className="text-right text-xs text-muted">
                      <div>Updated {new Date(page.updatedAt).toLocaleString()}</div>
                      <div>Path /{page.path || ''}</div>
                    </div>
                  ) : null}
                </div>
              </div>
              <div className="min-h-[420px] flex-1 px-6 py-6">
                {isLoading ? (
                  <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-10 text-center text-sm text-muted">
                    Loading page...
                  </div>
                ) : page ? (
                  <article className="prose prose-slate max-w-none dark:prose-invert prose-headings:tracking-tight prose-a:text-accent prose-pre:rounded-2xl prose-pre:bg-slate-950 prose-pre:text-slate-100">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{`# ${page.title}\n\n${editing ? draftContent : page.content}`}</ReactMarkdown>
                    {page.kind !== 'PAGE' && page.children.length > 0 ? (
                      <section className="mt-10 rounded-3xl border border-surface-border bg-surface-alt/60 p-5">
                        <div className="flex items-center justify-between gap-3">
                          <h3 className="mt-0 text-lg font-semibold text-foreground">Child pages</h3>
                          <button
                            type="button"
                            className="action-button-secondary"
                            onClick={() => setDialogState({ type: 'sort' })}
                            disabled={page.children.length < 2}
                          >
                            <ArrowDownUp size={16} />
                            Sort children
                          </button>
                        </div>
                        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                          {page.children.map((childNode) => (
                            <button
                              type="button"
                              key={childNode.path || childNode.slug}
                              className="rounded-2xl border border-surface-border bg-surface px-4 py-4 text-left transition hover:-translate-y-0.5 hover:border-accent/40"
                              onClick={() => handleNavigate(childNode.path)}
                            >
                              <div className="text-xs uppercase tracking-[0.18em] text-muted">
                                {childNode.kind}
                              </div>
                              <div className="mt-2 text-base font-medium text-foreground">
                                {childNode.title}
                              </div>
                            </button>
                          ))}
                        </div>
                      </section>
                    ) : null}
                  </article>
                ) : (
                  <div className="rounded-2xl border border-dashed border-surface-border bg-surface-alt/60 px-4 py-10 text-center text-sm text-muted">
                    Select a page to begin.
                  </div>
                )}
              </div>
            </section>

            <section className="surface-card flex min-h-[520px] flex-col overflow-hidden">
              <div className="border-b border-surface-border bg-surface-alt/70 px-5 py-4">
                <p className="text-sm font-semibold text-foreground">Editor</p>
                <p className="mt-1 text-sm text-muted">
                  Markdown editor with live preview, slug control, and keyboard save shortcut.
                </p>
              </div>
              <div className="flex-1 space-y-5 px-5 py-5">
                <label className="field">
                  <span className="text-sm font-medium">Title</span>
                  <input
                    className="field-input"
                    value={draftTitle}
                    onChange={(event) => {
                      const nextTitle = event.target.value
                      setDraftTitle(nextTitle)
                      if (!draftSlug) {
                        setDraftSlug(inferSlug(nextTitle))
                      }
                    }}
                    disabled={!page}
                  />
                </label>
                <label className="field">
                  <span className="text-sm font-medium">Slug</span>
                  <input
                    className="field-input"
                    value={draftSlug}
                    onChange={(event) => setDraftSlug(event.target.value)}
                    placeholder={inferSlug(draftTitle)}
                    disabled={!page || page.path === ''}
                  />
                </label>
                <label className="field flex-1">
                  <span className="text-sm font-medium">Markdown</span>
                  <textarea
                    className="field-input min-h-[360px] flex-1 resize-y"
                    value={draftContent}
                    onChange={(event) => setDraftContent(event.target.value)}
                    disabled={!page}
                    placeholder="Write markdown content here"
                  />
                </label>
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-surface-border bg-surface-alt/60 px-4 py-3">
                  <div>
                    <p className="text-sm font-medium text-foreground">
                      {isDirty ? 'You have unsaved changes' : 'All changes saved'}
                    </p>
                    <p className="mt-1 text-sm text-muted">
                      Use Ctrl/Cmd + S to save. Create pages, sections, move, copy, sort, and delete from the toolbar.
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      className="action-button-secondary"
                      onClick={() => {
                        if (!page) {
                          return
                        }
                        setDraftTitle(page.title)
                        setDraftSlug(page.slug)
                        setDraftContent(page.content)
                      }}
                      disabled={!isDirty}
                    >
                      Reset
                    </button>
                    <button
                      type="button"
                      className="action-button-primary"
                      onClick={() => {
                        void handleSave()
                      }}
                      disabled={!page || !isDirty}
                    >
                      Save page
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </main>
      </div>

      <SearchDialog
        open={searchOpen}
        tree={tree}
        onOpenChange={setSearchOpen}
        onNavigate={handleNavigate}
      />

      <CreatePageDialog
        open={dialogState.type === 'create'}
        parentPath={dialogState.type === 'create' ? dialogState.parentPath : ''}
        kind={dialogState.type === 'create' ? dialogState.kind : 'PAGE'}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={handleCreate}
      />

      <MoveCopyDialog
        mode="move"
        open={dialogState.type === 'move'}
        currentPath={page?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('move', payload)}
      />

      <MoveCopyDialog
        mode="copy"
        open={dialogState.type === 'copy'}
        currentPath={page?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('copy', payload)}
      />

      <DeleteDialog
        open={dialogState.type === 'delete'}
        path={page?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onConfirm={handleDelete}
      />

      <SortChildrenDialog
        open={dialogState.type === 'sort'}
        page={
          page && page.kind !== 'PAGE'
            ? {
                path: page.path,
                parentPath: page.parentPath,
                title: page.title,
                slug: page.slug,
                kind: page.kind,
                hasChildren: page.children.length > 0,
                children: page.children,
              }
            : null
        }
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={handleSort}
      />
    </div>
  )
}
