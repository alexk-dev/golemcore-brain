import { useEffect, useMemo, useRef, useState } from 'react'
import type { ClipboardEvent as ReactClipboardEvent } from 'react'
import type { EditorView } from '@codemirror/view'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { uploadAsset } from '../../lib/api'
import { editorPathToRoute, normalizeWikiPath, pathToRoute } from '../../lib/paths'
import { AssetManagerDialog } from '../assets/AssetManagerDialog'
import { buildDefaultMarkdownForAsset } from '../assets/assetMarkdown'
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

function derivePagePathFromLocation(pathname: string) {
  const normalized = normalizeWikiPath(pathname)
  if (!normalized.startsWith('e/')) {
    return normalized
  }
  return normalized.slice(2)
}

function getPastedFile(event: ReactClipboardEvent<HTMLDivElement>): File | null {
  const itemList = Array.from(event.clipboardData.items)
  for (const item of itemList) {
    if (item.kind !== 'file') {
      continue
    }
    const file = item.getAsFile()
    if (file) {
      return file
    }
  }
  return event.clipboardData.files[0] ?? null
}

export function PageEditor() {
  const location = useLocation()
  const navigate = useNavigate()
  const editorViewRef = useRef<EditorView | null>(null)
  const [previewVisible, setPreviewVisible] = useState(true)
  const [assetManagerOpen, setAssetManagerOpen] = useState(false)
  const [showUnsavedDialog, setShowUnsavedDialog] = useState(false)
  const [pendingNavigationPath, setPendingNavigationPath] = useState<string | null>(null)
  const [showMetadataPanel, setShowMetadataPanel] = useState(false)
  const [isDark, setIsDark] = useState(() => document.documentElement.classList.contains('dark'))

  const loadPageData = useEditorStore((state) => state.loadPageData)
  const savePage = useEditorStore((state) => state.savePage)
  const page = useEditorStore((state) => state.page)
  const initialPage = useEditorStore((state) => state.initialPage)
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

  const currentPath = useMemo(() => derivePagePathFromLocation(location.pathname), [location.pathname])

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

  const hasUnsavedChanges =
    page !== null &&
    initialPage !== null &&
    (title !== initialPage.title || slug !== initialPage.slug || content !== initialPage.content)

  useEffect(() => {
    if (!hasUnsavedChanges) {
      return
    }
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasUnsavedChanges])

  useEffect(() => {
    const nextPath = derivePagePathFromLocation(location.pathname)
    if (!hasUnsavedChanges || nextPath === currentPath) {
      return
    }
    setPendingNavigationPath(nextPath)
    setShowUnsavedDialog(true)
    navigate(editorPathToRoute(currentPath), { replace: true })
  }, [currentPath, hasUnsavedChanges, location.pathname, navigate])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const modifier = event.metaKey || event.ctrlKey
      if (modifier && event.key.toLowerCase() === 's') {
        event.preventDefault()
        void handleSave()
      }
      if (modifier && event.key.toLowerCase() === 'e') {
        event.preventDefault()
        void handleCloseEditor()
      }
      if (modifier && event.shiftKey && event.key.toLowerCase() === 'm') {
        event.preventDefault()
        setShowMetadataPanel((value) => !value)
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
    } catch {
      // editor store error surface is the canonical visible feedback for tests/UI
    }
  }

  const handleCloseEditor = async () => {
    if (hasUnsavedChanges) {
      setPendingNavigationPath(page?.path ?? currentPath)
      setShowUnsavedDialog(true)
      return
    }
    navigate(pathToRoute(page?.path ?? currentPath))
  }

  const handleConfirmNavigation = async () => {
    const nextPath = pendingNavigationPath
    setShowUnsavedDialog(false)
    setPendingNavigationPath(null)
    navigate(pathToRoute(nextPath ?? currentPath))
  }

  const handleCancelNavigation = () => {
    setShowUnsavedDialog(false)
    setPendingNavigationPath(null)
  }

  const handlePaste = async (event: ReactClipboardEvent<HTMLDivElement>) => {
    if (!page) {
      return
    }
    const file = getPastedFile(event)
    if (!file) {
      return
    }
    event.preventDefault()
    try {
      const asset = await uploadAsset(page.path, file)
      insertMarkdownAtCursor(editorViewRef.current, buildDefaultMarkdownForAsset(asset), setContent)
      toast.success(`Uploaded ${asset.name}`)
    } catch (pasteError) {
      toast.error((pasteError as Error).message)
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
              onClick={() => void handleCloseEditor()}
            >
              <span className="editor-title-bar__title">{title}</span>
            </button>
            <span className="editor-title-bar__slug">/{page.path}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="action-button-secondary"
              onClick={() => setShowMetadataPanel((value) => !value)}
            >
              Edit metadata
            </button>
            <button
              type="button"
              className="action-button-secondary"
              onClick={() => void handleCloseEditor()}
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
            {showMetadataPanel ? (
              <div className="surface-card mb-4 p-4">
                <h2 className="mb-3 text-lg font-semibold">Metadata</h2>
                <label className="field">
                  <span className="text-sm font-medium">Title</span>
                  <input
                    className="field-input"
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                  />
                </label>
                <label className="field mt-4">
                  <span className="text-sm font-medium">Slug</span>
                  <input className="field-input" value={slug} onChange={(event) => setSlug(event.target.value)} />
                </label>
                <div className="mt-3 text-xs text-muted">Path: {(page.parentPath ? `${page.parentPath}/` : '') + slug}</div>
              </div>
            ) : null}
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
                    onPaste={handlePaste}
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

      {showUnsavedDialog ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="surface-card max-w-md p-6">
            <h2 className="mb-2 text-xl font-semibold">Unsaved changes</h2>
            <p className="mb-4 text-sm text-muted">
              You have unsaved changes. Do you want to discard them and continue?
            </p>
            <div className="flex justify-end gap-3">
              <button type="button" className="action-button-secondary" onClick={handleCancelNavigation}>
                Stay here
              </button>
              <button type="button" className="action-button-danger" onClick={() => void handleConfirmNavigation()}>
                Discard changes
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
