import { Link } from 'react-router-dom'
import { useMemo } from 'react'

import { editorPathToRoute, pathToRoute } from '../../lib/paths'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { LinkInfo } from '../links/LinkInfo'
import { NotFoundPage } from '../page/NotFoundPage'
import { MarkdownPreview } from '../preview/MarkdownPreview'
import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ensurePage } from '../../lib/api'
import { normalizeWikiPath } from '../../lib/paths'
import { useViewerStore } from '../../stores/viewer'

export function PageViewer() {
  const location = useLocation()
  const navigate = useNavigate()
  const page = useViewerStore((state) => state.page)
  const error = useViewerStore((state) => state.error)
  const loading = useViewerStore((state) => state.loading)
  const loadPageData = useViewerStore((state) => state.loadPageData)
  const setActiveNodeId = useTreeStore((state) => state.setActiveNodeId)
  const openAncestorsForPath = useTreeStore((state) => state.openAncestorsForPath)
  const getPageByPath = useTreeStore((state) => state.getPageByPath)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const currentUser = useUiStore((state) => state.currentUser)
  const canEdit = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'

  const currentPath = useMemo(
    () => normalizeWikiPath(location.pathname),
    [location.pathname],
  )
  const canCreate = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'

  useEffect(() => {
    void loadPageData(currentPath)
  }, [currentPath, loadPageData])

  useEffect(() => {
    const treeNode = getPageByPath(currentPath)
    setActiveNodeId(treeNode?.id ?? null)
    openAncestorsForPath(currentPath)
  }, [currentPath, getPageByPath, openAncestorsForPath, setActiveNodeId])

  const handleCreateByPath = async () => {
    try {
      const ensuredPage = await ensurePage(currentPath, currentPath.split('/').pop())
      await reloadTree()
      navigate(`/${ensuredPage.path}`)
    } catch (createError) {
      toast.error((createError as Error).message)
    }
  }

  const breadcrumbs = useMemo(() => {
    if (!page) {
      return []
    }
    const items: Array<{ title: string; path: string }> = [{ title: 'Home', path: '' }]
    if (!page.path) {
      return items
    }
    const segments = page.path.split('/')
    let accumulatedPath = ''
    for (const segment of segments) {
      accumulatedPath = accumulatedPath ? `${accumulatedPath}/${segment}` : segment
      const node = getPageByPath(accumulatedPath)
      items.push({
        title: node?.title ?? segment,
        path: accumulatedPath,
      })
    }
    return items
  }, [getPageByPath, page])

  if (loading) {
    return <div className="page-viewer__error">Loading page...</div>
  }

  if (error || !page) {
    if (currentPath) {
      return (
        <NotFoundPage
          path={currentPath}
          onCreate={() => void handleCreateByPath()}
          canCreate={canCreate}
          signInHref={canCreate ? null : '/login'}
        />
      )
    }
    return <div className="page-viewer__error">Error: {error ?? 'Page not found'}</div>
  }

  const isSection = page.kind !== 'PAGE'

  return (
    <div className="page-viewer">
      <div className="page-viewer__header">
        <nav className="flex flex-wrap items-center gap-2 text-sm text-muted" aria-label="Breadcrumbs">
          {breadcrumbs.map((breadcrumb, index) => (
            <div key={breadcrumb.path || 'root'} className="flex items-center gap-2">
              {index > 0 ? <span aria-hidden="true">/</span> : null}
              <Link to={pathToRoute(breadcrumb.path)} className="hover:text-foreground">
                {breadcrumb.title}
              </Link>
            </div>
          ))}
        </nav>
        <h1 className="page-viewer__title">{page.title}</h1>
        <div className="page-viewer__metadata">
          <span className="page-viewer__metadata-item">
            Updated {new Date(page.updatedAt).toLocaleString()}
          </span>
          {canEdit ? (
            <Link to={editorPathToRoute(page.path)} className="action-button-secondary">
              Edit page
            </Link>
          ) : null}
        </div>
      </div>
      <div className="page-viewer__body">
        <article className="page-viewer__content">
          <MarkdownPreview content={page.content} path={page.path} darkMode={true} />
          {isSection ? (
            <section className="mt-8 rounded-2xl border border-surface-border bg-surface-alt/50 p-4">
              <h2 className="mb-3 text-lg font-semibold">Section contents</h2>
              {page.children.length > 0 ? (
                <ul className="space-y-2">
                  {page.children.map((child) => (
                    <li key={child.path}>
                      <Link to={pathToRoute(child.path)} className="text-sm font-medium hover:text-accent">
                        {child.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted">This section does not contain any child pages yet.</p>
              )}
            </section>
          ) : null}
        </article>
        <LinkInfo />
      </div>
    </div>
  )
}
