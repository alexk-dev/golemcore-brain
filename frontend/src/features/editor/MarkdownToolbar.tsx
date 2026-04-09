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
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '**')}>
        <Bold className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '_')}>
        <Italic className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '~~')}>
        <Strikethrough className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '[', '](https://example.com)')}>
        <Link className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '# ')}>
        H1
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '## ')}>
        H2
      </button>
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={() => insertText(editorViewRef.current, '### ')}>
        H3
      </button>
      <div className="markdown-toolbar__separator markdown-toolbar__separator--desktop-only" />
      <button type="button" className="markdown-toolbar__button" onClick={() => insertText(editorViewRef.current, '| Header 1 | Header 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |')}>
        <Table className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => wrapSelection(editorViewRef.current, '```\n', '\n```')}>
        <Code className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={onOpenAssetManager}>
        <Image className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button" onClick={() => editorViewRef.current && undo(editorViewRef.current)}>
        <Undo className="markdown-toolbar__icon" />
      </button>
      <button type="button" className="markdown-toolbar__button" onClick={() => editorViewRef.current && redo(editorViewRef.current)}>
        <Redo className="markdown-toolbar__icon" />
      </button>
      <div className="markdown-toolbar__separator" />
      <button type="button" className="markdown-toolbar__button markdown-toolbar__button--desktop-only" onClick={onTogglePreview}>
        {previewVisible ? <EyeOff className="markdown-toolbar__icon" /> : <Eye className="markdown-toolbar__icon" />}
      </button>
    </div>
  )
}
