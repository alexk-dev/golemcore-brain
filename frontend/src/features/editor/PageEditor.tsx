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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ClipboardEvent as ReactClipboardEvent } from 'react'
import type { EditorView } from '@codemirror/view'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { uploadAsset } from '../../lib/api'
import { editorPathToRoute, normalizeWikiPath, pathToRoute } from '../../lib/paths'
import { InsertWikiLinkDialog } from '../../components/InsertWikiLinkDialog'
import { AssetManagerDialog } from '../assets/AssetManagerDialog'
import { buildDefaultMarkdownForAsset } from '../assets/assetMarkdown'
import { MarkdownPreview } from '../preview/MarkdownPreview'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { MarkdownCodeEditor } from './MarkdownCodeEditor'
import { MarkdownToolbar } from './MarkdownToolbar'
import type { WikiAsset, WikiTreeNode } from '../../types'

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

function formatTimestamp(timestamp?: string) {
  if (!timestamp) {
    return 'unknown time'
  }
  return new Date(timestamp).toLocaleString()
}

function legacyAssetPath(assetPath: string): string {
  return assetPath.replace(/^\/api\/spaces\/[^/]+\/assets/, '/api/assets')
}

function replaceAssetReferences(content: string, oldAsset: WikiAsset, newAsset: WikiAsset) {
  return content
    .replaceAll(oldAsset.path, newAsset.path)
    .replaceAll(legacyAssetPath(oldAsset.path), newAsset.path)
    .replaceAll(`[${oldAsset.name}](`, `[${newAsset.name}](`)
}

export function PageEditor() {
  const location = useLocation()
  const navigate = useNavigate()
  const editorViewRef = useRef<EditorView | null>(null)
  const [previewVisible, setPreviewVisible] = useState(true)
  const [assetManagerOpen, setAssetManagerOpen] = useState(false)
  const [wikiLinkDialogOpen, setWikiLinkDialogOpen] = useState(false)
  const [wikiLinkInitialQuery, setWikiLinkInitialQuery] = useState('')
  const pendingLinkSelectionRef = useRef<{ from: number; to: number } | null>(null)
  const [showUnsavedDialog, setShowUnsavedDialog] = useState(false)
  const [pendingNavigationPath, setPendingNavigationPath] = useState<string | null>(null)
  const [showMetadataPanel, setShowMetadataPanel] = useState(false)
  const [showConflictDialog, setShowConflictDialog] = useState(false)
  const [activeMobilePane, setActiveMobilePane] = useState<'editor' | 'preview'>('editor')
  const [assetPreviewVersion, setAssetPreviewVersion] = useState(0)
  const previewScrollRef = useRef<HTMLDivElement | null>(null)

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
  const conflict = useEditorStore((state) => state.conflict)
  const reloadFromConflict = useEditorStore((state) => state.reloadFromConflict)
  const mergeConflictWithLocalDraft = useEditorStore((state) => state.mergeConflictWithLocalDraft)
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
    if (conflict) {
      setShowConflictDialog(true)
    }
  }, [conflict])

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
      if (modifier && !event.shiftKey && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        openWikiLinkPicker()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  })

  const openWikiLinkPicker = () => {
    const view = editorViewRef.current
    if (!view) {
      pendingLinkSelectionRef.current = null
      setWikiLinkInitialQuery('')
      setWikiLinkDialogOpen(true)
      return
    }
    const selection = view.state.selection.main
    const selectedText = view.state.sliceDoc(selection.from, selection.to)
    pendingLinkSelectionRef.current = { from: selection.from, to: selection.to }
    setWikiLinkInitialQuery(selectedText.trim())
    setWikiLinkDialogOpen(true)
  }

  const handleInsertWikiLink = (page: Pick<WikiTreeNode, 'path' | 'title'>) => {
    const view = editorViewRef.current
    const targetPath = `/${page.path}`
    const pending = pendingLinkSelectionRef.current
    pendingLinkSelectionRef.current = null
    if (!view) {
      const markdown = `[${page.title}](${targetPath})`
      setContent(`${content}${content && !content.endsWith('\n') ? ' ' : ''}${markdown}`)
      return
    }
    const selection = view.state.selection.main
    const range = pending ?? { from: selection.from, to: selection.to }
    const selectedText = view.state.sliceDoc(range.from, range.to)
    const label = selectedText.trim().length > 0 ? selectedText : page.title
    const markdown = `[${label}](${targetPath})`
    view.dispatch({
      changes: { from: range.from, to: range.to, insert: markdown },
      selection: { anchor: range.from + markdown.length },
    })
    view.focus()
    setContent(view.state.doc.toString())
  }

  const handleSave = async () => {
    if (!hasUnsavedChanges) {
      return
    }
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

  const handleReloadLatest = () => {
    reloadFromConflict()
    setShowConflictDialog(false)
    toast.success('Reloaded the latest saved version')
  }

  const handleMergeWithLatest = () => {
    mergeConflictWithLocalDraft()
    setShowConflictDialog(false)
    toast.success('Latest version loaded as the new base. Your draft was preserved for manual merge.')
  }

  const bumpAssetPreviewVersion = useCallback(() => setAssetPreviewVersion(Date.now()), [])

  const handleAssetRenamed = (oldAsset: WikiAsset, newAsset: WikiAsset) => {
    const nextContent = replaceAssetReferences(content, oldAsset, newAsset)
    if (nextContent !== content) {
      setContent(nextContent)
      toast.success(`Updated references to ${newAsset.name}`)
    }
  }

  const handleCursorLineChange = useCallback((line: number, lineCount: number) => {
    const preview = previewScrollRef.current
    if (!preview || lineCount <= 1) {
      return
    }
    const maxScroll = preview.scrollHeight - preview.clientHeight
    if (maxScroll <= 0) {
      return
    }
    const ratio = Math.min(Math.max((line - 1) / (lineCount - 1), 0), 1)
    preview.scrollTo({ top: maxScroll * ratio, behavior: 'smooth' })
  }, [])

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
      bumpAssetPreviewVersion()
      toast.success(`Uploaded ${asset.name}`)
    } catch (pasteError) {
      toast.error((pasteError as Error).message)
    }
  }

  if (loading) {
    return <div className="page-editor__error">Loading page...</div>
  }

  if (!page) {
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
              {hasUnsavedChanges ? (
                <span className="editor-title-bar__dirty-indicator">Unsaved changes</span>
              ) : null}
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
            <button
              type="button"
              className="action-button-primary disabled:cursor-not-allowed disabled:opacity-60"
              onClick={() => void handleSave()}
              disabled={!hasUnsavedChanges}
              title="Save page (Ctrl+S)"
            >
              {hasUnsavedChanges ? 'Save changes' : 'Saved'}
            </button>
          </div>
        </div>
        {conflict ? (
          <div className="mx-6 mt-4 rounded-2xl border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-warning">
            Another session saved this page at {formatTimestamp(conflict.updatedAt)}. Your draft is still in the editor.
            <div className="mt-3 flex flex-wrap gap-2">
              <button type="button" className="action-button-secondary" onClick={() => setShowConflictDialog(true)}>
                Review conflict
              </button>
              <button type="button" className="action-button-secondary" onClick={handleMergeWithLatest}>
                Merge with latest
              </button>
              <button type="button" className="action-button-danger" onClick={handleReloadLatest}>
                Reload latest
              </button>
            </div>
          </div>
        ) : null}
        {error && !conflict ? (
          <div className="mx-6 mt-4 rounded-2xl border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
            {error}
          </div>
        ) : null}
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
              <div className="markdown-editor__mobile-tabs" role="tablist" aria-label="Editor panes">
                <button
                  type="button"
                  className={activeMobilePane === 'editor' ? 'markdown-editor__mobile-tab markdown-editor__mobile-tab--active' : 'markdown-editor__mobile-tab'}
                  onClick={() => setActiveMobilePane('editor')}
                >
                  Editor
                </button>
                <button
                  type="button"
                  className={activeMobilePane === 'preview' ? 'markdown-editor__mobile-tab markdown-editor__mobile-tab--active' : 'markdown-editor__mobile-tab'}
                  onClick={() => setActiveMobilePane('preview')}
                >
                  Preview
                </button>
              </div>
              <MarkdownToolbar
                editorViewRef={editorViewRef}
                previewVisible={previewVisible}
                onTogglePreview={() => setPreviewVisible((value) => !value)}
                onOpenAssetManager={() => setAssetManagerOpen(true)}
                onOpenWikiLinkPicker={openWikiLinkPicker}
              />
              <div className="flex flex-1 overflow-hidden">
                <div className={`${previewVisible ? 'markdown-editor__editor-pane markdown-editor__editor-pane--half' : 'markdown-editor__editor-pane markdown-editor__editor-pane--full'} ${activeMobilePane === 'preview' ? 'markdown-editor__pane--mobile-hidden' : ''}`}>
                  <MarkdownCodeEditor
                    key={page.id}
                    value={content}
                    onChange={setContent}
                    editorViewRef={editorViewRef}
                    onPaste={handlePaste}
                    onCursorLineChange={handleCursorLineChange}
                  />
                </div>
                {previewVisible ? (
                  <div className={`markdown-editor__preview-container ${activeMobilePane === 'editor' ? 'markdown-editor__pane--mobile-hidden' : ''}`}>
                    <div className="markdown-editor__preview" ref={previewScrollRef}>
                      <div className="markdown-editor__preview-inner">
                        <MarkdownPreview content={content} path={page.path} darkMode={true} assetVersion={assetPreviewVersion} />
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
        onAssetRenamed={handleAssetRenamed}
        onAssetsChanged={bumpAssetPreviewVersion}
      />

      <InsertWikiLinkDialog
        open={wikiLinkDialogOpen}
        initialQuery={wikiLinkInitialQuery}
        onOpenChange={(open) => {
          setWikiLinkDialogOpen(open)
          if (!open) {
            pendingLinkSelectionRef.current = null
          }
        }}
        onSelect={handleInsertWikiLink}
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

      {showConflictDialog && conflict ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="surface-card max-h-[90vh] w-full max-w-5xl overflow-auto p-6">
            <h2 className="mb-2 text-xl font-semibold">Page changed in another session</h2>
            <p className="mb-4 text-sm text-muted">
              The latest saved version was updated at {formatTimestamp(conflict.updatedAt)}. Reload it to discard your draft, or rebase your draft onto the latest revision for a manual merge.
            </p>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="rounded-2xl border border-surface-border bg-surface-alt/60 p-4">
                <div className="mb-2 text-sm font-semibold">Your draft</div>
                <div className="mb-2 text-xs text-muted">/{(page.parentPath ? `${page.parentPath}/` : '') + slug}</div>
                <div className="mb-2 text-sm font-medium">{title}</div>
                <pre className="max-h-80 overflow-auto whitespace-pre-wrap text-xs text-muted">{content || '(empty)'}</pre>
              </div>
              <div className="rounded-2xl border border-surface-border bg-surface-alt/60 p-4">
                <div className="mb-2 text-sm font-semibold">Latest saved version</div>
                <div className="mb-2 text-xs text-muted">/{conflict.path}</div>
                <div className="mb-2 text-sm font-medium">{conflict.title}</div>
                <pre className="max-h-80 overflow-auto whitespace-pre-wrap text-xs text-muted">{conflict.content || '(empty)'}</pre>
              </div>
            </div>
            <div className="mt-6 flex flex-wrap justify-end gap-3">
              <button type="button" className="action-button-secondary" onClick={() => setShowConflictDialog(false)}>
                Stay on draft
              </button>
              <button type="button" className="action-button-secondary" onClick={handleMergeWithLatest}>
                Merge with latest
              </button>
              <button type="button" className="action-button-danger" onClick={handleReloadLatest}>
                Reload latest
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
