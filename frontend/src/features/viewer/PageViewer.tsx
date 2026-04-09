import { useEffect, useMemo } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { ensurePage } from '../../lib/api'
import { normalizeWikiPath } from '../../lib/paths'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'
import { LinkInfo } from '../links/LinkInfo'
import { NotFoundPage } from '../page/NotFoundPage'
import { MarkdownPreview } from '../preview/MarkdownPreview'

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
  const isDark = useUiStore((state) => state.isDark)

  const currentPath = useMemo(
    () => normalizeWikiPath(location.pathname),
    [location.pathname],
  )

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

  if (loading) {
    return <div className="page-viewer__error">Loading page...</div>
  }

  if (error || !page) {
    if (currentPath) {
      return <NotFoundPage path={currentPath} onCreate={() => void handleCreateByPath()} />
    }
    return <div className="page-viewer__error">Error: {error ?? 'Page not found'}</div>
  }

  return (
    <div className="page-viewer">
      <div className="page-viewer__header">
        <div className="page-viewer__metadata">
          <span className="page-viewer__metadata-item">
            Updated {new Date(page.updatedAt).toLocaleString()}
          </span>
        </div>
      </div>
      <div className="page-viewer__body">
        <article className="page-viewer__content">
          <MarkdownPreview content={page.content} path={page.path} darkMode={isDark} />
        </article>
        <LinkInfo />
      </div>
    </div>
  )
}
