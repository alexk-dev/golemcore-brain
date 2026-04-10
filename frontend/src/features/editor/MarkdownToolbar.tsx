import { Bold, Code, Eye, EyeOff, Image, Italic, Link, Redo, Strikethrough, Table, Undo } from 'lucide-react'
import type { RefObject } from 'react'
import type { EditorView } from '@codemirror/view'
import { redo, undo } from '@codemirror/commands'

interface MarkdownToolbarProps {
  editorViewRef: RefObject<EditorView | null>
  previewVisible: boolean
  onTogglePreview: () => void
  onOpenAssetManager: () => void
}

function insertText(view: EditorView | null, text: string) {
  if (!view) {
    return
  }
  const selection = view.state.selection.main
  view.dispatch({
    changes: { from: selection.from, to: selection.to, insert: text },
    selection: { anchor: selection.from + text.length },
  })
}

function wrapSelection(view: EditorView | null, before: string, after = before) {
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

export function MarkdownToolbar({
  editorViewRef,
  previewVisible,
  onTogglePreview,
  onOpenAssetManager,
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
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '[', '](https://example.com)')} title="Link" aria-label="Link">
        <Link className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '# ')} title="Heading 1 (Ctrl+Alt+1)" aria-label="Heading 1">
        H1
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '## ')} title="Heading 2 (Ctrl+Alt+2)" aria-label="Heading 2">
        H2
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '### ')} title="Heading 3 (Ctrl+Alt+3)" aria-label="Heading 3">
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
