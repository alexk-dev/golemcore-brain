import { redo, undo } from '@codemirror/commands'
import type { EditorView } from '@codemirror/view'
import { Bold, Code, Eye, EyeOff, FileSearch, Image, Italic, Link, Redo, Strikethrough, Table, Undo } from 'lucide-react'
import type { RefObject } from 'react'

import { applyHeadingToSelection, applyTextTransform } from './markdownShortcuts'

interface MarkdownToolbarProps {
  editorViewRef: RefObject<EditorView | null>
  previewVisible: boolean
  onTogglePreview: () => void
  onOpenAssetManager: () => void
  onOpenWikiLinkPicker: () => void
}

function insertText(view: EditorView | null, text: string): void {
  if (!view) {
    return
  }
  const selection = view.state.selection.main
  view.dispatch({
    changes: { from: selection.from, to: selection.to, insert: text },
    selection: { anchor: selection.from + text.length },
  })
}

function wrapSelection(view: EditorView | null, before: string, after = before): void {
  if (!view) {
    return
  }
  const selection = view.state.selection.main
  const selectedText = view.state.sliceDoc(selection.from, selection.to)
  view.dispatch({
    changes: {
      from: selection.from,
      to: selection.to,
      insert: `${before}${selectedText}${after}`,
    },
    selection: { anchor: selection.from + before.length + selectedText.length + after.length },
  })
}

function applyHeading(view: EditorView | null, level: 1 | 2 | 3): void {
  if (!view) {
    return
  }
  applyTextTransform(view, (text: string, selectionStart: number, selectionEnd: number) =>
    applyHeadingToSelection(text, selectionStart, selectionEnd, level))
}

export function MarkdownToolbar({
  editorViewRef,
  previewVisible,
  onTogglePreview,
  onOpenAssetManager,
  onOpenWikiLinkPicker,
}: MarkdownToolbarProps) {
  return (
    <div className="markdown-toolbar">
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '**')} title="Bold (Ctrl+B)" aria-label="Bold">
        <Bold className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '_')} title="Italic (Ctrl+I)" aria-label="Italic">
        <Italic className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '~~')} title="Strikethrough" aria-label="Strikethrough">
        <Strikethrough className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '[', '](https://example.com)')} title="External link" aria-label="External link">
        <Link className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={onOpenWikiLinkPicker} title="Link to wiki page (Ctrl+K)" aria-label="Link to wiki page">
        <FileSearch className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => applyHeading(editorViewRef.current, 1)} title="Heading 1 (Ctrl+Alt+1)" aria-label="Heading 1">
        H1
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => applyHeading(editorViewRef.current, 2)} title="Heading 2 (Ctrl+Alt+2)" aria-label="Heading 2">
        H2
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => applyHeading(editorViewRef.current, 3)} title="Heading 3 (Ctrl+Alt+3)" aria-label="Heading 3">
        H3
      </button>
      <div className="markdown-toolbar__separator markdown-toolbar__separator--desktop-only" />
      <button type="button" className="markdown-toolbar__button" onClick={() => insertText(editorViewRef.current, '| Header 1 | Header 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |')} title="Table" aria-label="Table">
        <Table className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '```\n', '\n```')} title="Code block" aria-label="Code block">
        <Code className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={onOpenAssetManager} title="Add image or file" aria-label="Add image or file">
        <Image className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button" onClick={() => editorViewRef.current && undo(editorViewRef.current)} title="Undo" aria-label="Undo">
        <Undo className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => editorViewRef.current && redo(editorViewRef.current)} title="Redo" aria-label="Redo">
        <Redo className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={onTogglePreview} title={previewVisible ? 'Hide preview' : 'Show preview'} aria-label={previewVisible ? 'Hide preview' : 'Show preview'}>
        {previewVisible ? <EyeOff className="markdown-toolbar__icon" /> : <Eye className="markdown-toolbar__icon" />}
      </button>
    </div>
  )
}
