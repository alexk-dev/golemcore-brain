import { useEffect, useMemo, useRef, useState } from 'react'
import type { EditorView } from '@codemirror/view'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { editorPathToRoute, normalizeWikiPath, pathToRoute } from '../../lib/paths'
import { AssetManagerDialog } from '../assets/AssetManagerDialog'
import { MarkdownPreview } from '../preview/MarkdownPreview'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { MarkdownCodeEditor } from './MarkdownCodeEditor'
import { MarkdownToolbar } from './MarkdownToolbar'

function insertMarkdownAtCursor(view: EditorView | null, markdown: string, onChange: (value: string) => void) {
  if (!view) {
    return
  }
  const selection = view.state.selection.main
  view.dispatch({
    changes: {
      from: selection.from,
      to: selection.to,
      insert: markdown,
    },
    selection: { anchor: selection.from + markdown.length },
  })
  onChange(view.state.doc.toString())
}

export function PageEditor() {
  const location = useLocation()
  const navigate = useNavigate()
  const editorViewRef = useRef<EditorView | null>(null)
  const [previewVisible, setPreviewVisible] = useState(true)
  const [assetManagerOpen, setAssetManagerOpen] = useState(false)
  const [isDark, setIsDark] = useState(() => document.documentElement.classList.contains('dark'))

  const loadPageData = useEditorStore((state) => state.loadPageData)
  const savePage = useEditorStore((state) => state.savePage)
  const page = useEditorStore((state) => state.page)
  const title = useEditorStore((state) => state.title)
  const slug = useEditorStore((state) => state.slug)
  const content = useEditorStore((state) => state.content)
  const setTitle = useEditorStore((state) => state.setTitle)
  const setSlug = useEditorStore((state) => state.setSlug)
  const setContent = useEditorStore((state) => state.setContent)
  const loading = useEditorStore((state) => state.loading)
  const error = useEditorStore((state) => state.error)
  const setActiveNodeId = useTreeStore((state) => state.setActiveNodeId)
  const openAncestorsForPath = useTreeStore((state) => state.openAncestorsForPath)
  const getPageByPath = useTreeStore((state) => state.getPageByPath)
  const reloadTree = useTreeStore((state) => state.reloadTree)

  const currentPath = useMemo(() => {
    const normalized = normalizeWikiPath(location.pathname)
    if (!normalized.startsWith('e/')) {
      return normalized
    }
    return normalized.slice(2)
  }, [location.pathname])

  useEffect(() => {
    void loadPageData(currentPath)
  }, [currentPath, loadPageData])

  useEffect(() => {
    const treeNode = getPageByPath(currentPath)
    setActiveNodeId(treeNode?.id ?? null)
    openAncestorsForPath(currentPath)
  }, [currentPath, getPageByPath, openAncestorsForPath, setActiveNodeId])

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains('dark'))
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const modifier = event.metaKey || event.ctrlKey
      if (modifier && event.key.toLowerCase() === 's') {
        event.preventDefault()
        void handleSave()
      }
      if (modifier && event.key.toLowerCase() === 'e') {
        event.preventDefault()
        navigate(pathToRoute(page?.path ?? currentPath))
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  })

  const handleSave = async () => {
    try {
      const updatedPage = await savePage()
      if (!updatedPage) {
        return
      }
      await reloadTree()
      toast.success('Page saved successfully')
      navigate(editorPathToRoute(updatedPage.path), { replace: true })
    } catch (saveError) {
      toast.error((saveError as Error).message)
    }
  }

  if (loading) {
    return <div className="page-editor__error">Loading page...</div>
  }

  if (error || !page) {
    return <div className="page-editor__error">Error: {error ?? 'Page not found'}</div>
  }

  return (
    <>
      <div className="page-editor">
        <div className="page-editor__toolbar">
          <div className="editor-title-bar">
            <button
              type="button"
              className="editor-title-bar__button"
              onClick={() => navigate(pathToRoute(page.path))}
            >
              <span className="editor-title-bar__title">{title}</span>
            </button>
            <span className="editor-title-bar__slug">/{page.path}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="action-button-secondary"
              onClick={() => navigate(pathToRoute(page.path))}
            >
              Close editor
            </button>
            <button type="button" className="action-button-primary" onClick={() => void handleSave()}>
              Save
            </button>
          </div>
        </div>
        <div className="page-editor__grid">
          <div className="page-editor__form">
            <label className="field">
              <span className="text-sm font-medium">Title</span>
              <input
                className="field-input"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>
            <label className="field">
              <span className="text-sm font-medium">Slug</span>
              <input className="field-input" value={slug} onChange={(event) => setSlug(event.target.value)} />
            </label>
            <div className="markdown-editor">
              <MarkdownToolbar
                editorViewRef={editorViewRef}
                previewVisible={previewVisible}
                onTogglePreview={() => setPreviewVisible((value) => !value)}
                onOpenAssetManager={() => setAssetManagerOpen(true)}
              />
              <div className="flex flex-1 overflow-hidden">
                <div className={previewVisible ? 'markdown-editor__editor-pane markdown-editor__editor-pane--half' : 'markdown-editor__editor-pane markdown-editor__editor-pane--full'}>
                  <MarkdownCodeEditor
                    value={content}
                    darkMode={isDark}
                    onChange={setContent}
                    editorViewRef={editorViewRef}
                  />
                </div>
                {previewVisible ? (
                  <div className="markdown-editor__preview-container">
                    <div className="markdown-editor__preview">
                      <div className="markdown-editor__preview-inner">
                        <MarkdownPreview content={content} path={page.path} darkMode={isDark} />
                      </div>
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      </div>

      <AssetManagerDialog
        open={assetManagerOpen}
        pagePath={page.path}
        onOpenChange={setAssetManagerOpen}
        onInsertMarkdown={(markdown) => insertMarkdownAtCursor(editorViewRef.current, markdown, setContent)}
      />
    </>
  )
}
