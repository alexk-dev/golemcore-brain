import { autocompletion, closeCompletion, completionStatus, type Completion, type CompletionContext, type CompletionResult } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { markdown } from '@codemirror/lang-markdown'
import { Compartment, EditorState } from '@codemirror/state'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorView, keymap } from '@codemirror/view'
import { useEffect, useRef, useState } from 'react'
import type { ClipboardEventHandler, RefObject } from 'react'

import { useTreeStore } from '../../stores/tree'
import { applyHeadingToSelection, applyTextTransform, wrapSelectionText, type TextTransform } from './markdownShortcuts'

type InternalLinkCompletion = Completion & {
  path: string
}

interface MarkdownCodeEditorProps {
  value: string
  onChange: (value: string) => void
  editorViewRef: RefObject<EditorView | null>
  onPaste?: ClipboardEventHandler<HTMLDivElement>
  onCursorLineChange?: (line: number, lineCount: number) => void
}

const runTextTransform = (transform: TextTransform) => (view: EditorView) => {
  applyTextTransform(view, transform)
  return true
}

function getLinkTargetRange(context: CompletionContext) {
  const { state, pos } = context
  const line = state.doc.lineAt(pos)
  const beforeCursor = line.text.slice(0, pos - line.from)
  const afterCursor = line.text.slice(pos - line.from)
  const match = beforeCursor.match(/!?\[[^\]]*\]\(([^)\s]*)$/)

  if (!match) {
    return null
  }

  const typedTarget = match[1] ?? ''
  const suffix = afterCursor.match(/^[^)\s]*/)?.[0] ?? ''
  return {
    from: pos - typedTarget.length,
    to: pos + suffix.length,
    query: typedTarget.startsWith('/') ? typedTarget.slice(1) : typedTarget,
  }
}

function internalLinkCompletionSource(context: CompletionContext): CompletionResult | null {
  const range = getLinkTargetRange(context)
  if (!range) {
    return null
  }

  const items = useTreeStore.getState().flatPages
  if (items.length === 0) {
    return null
  }

  const normalizedQuery = range.query.trim().toLowerCase()
  const matches = items
    .filter((item) => `${item.title} ${item.path}`.toLowerCase().includes(normalizedQuery))
    .slice(0, 20)

  if (matches.length === 0) {
    return null
  }

  const options: InternalLinkCompletion[] = matches.map((item) => ({
    label: item.title,
    displayLabel: item.title,
    info: `/${item.path}`,
    type: 'text',
    apply: `/${item.path}`,
    path: item.path,
  }))

  return {
    from: range.from,
    to: range.to,
    options,
    filter: false,
  }
}

export function MarkdownCodeEditor({
  value,
  onChange,
  editorViewRef,
  onPaste,
  onCursorLineChange,
}: MarkdownCodeEditorProps) {
  const editorRef = useRef<HTMLDivElement | null>(null)
  const viewRef = useRef<EditorView | null>(null)
  const initialValueRef = useRef(value)
  const valueRef = useRef(value)
  const onChangeRef = useRef(onChange)
  const [themeCompartment] = useState(() => new Compartment())

  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    valueRef.current = value
  }, [value])

  useEffect(() => {
    if (!editorRef.current) {
      return
    }

    const updateListener = EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        const nextValue = update.state.doc.toString()
        valueRef.current = nextValue
        onChangeRef.current(nextValue)
      }
      if (update.selectionSet && onCursorLineChange) {
        const pos = update.state.selection.main.head
        onCursorLineChange(update.state.doc.lineAt(pos).number, update.state.doc.lines)
      }
    })

    const customShortcuts = [
      {
        key: 'Escape',
        run: (view: EditorView) => {
          if (completionStatus(view.state) === null) {
            return false
          }
          return closeCompletion(view)
        },
        stopPropagation: true,
      },
      {
        key: 'Mod-b',
        run: runTextTransform((text, selectionStart, selectionEnd) =>
          wrapSelectionText(text, selectionStart, selectionEnd, '**')),
      },
      {
        key: 'Mod-i',
        run: runTextTransform((text, selectionStart, selectionEnd) =>
          wrapSelectionText(text, selectionStart, selectionEnd, '_')),
      },
      {
        key: 'Mod-Alt-1',
        run: runTextTransform((text, selectionStart, selectionEnd) =>
          applyHeadingToSelection(text, selectionStart, selectionEnd, 1)),
      },
      {
        key: 'Mod-Alt-2',
        run: runTextTransform((text, selectionStart, selectionEnd) =>
          applyHeadingToSelection(text, selectionStart, selectionEnd, 2)),
      },
      {
        key: 'Mod-Alt-3',
        run: runTextTransform((text, selectionStart, selectionEnd) =>
          applyHeadingToSelection(text, selectionStart, selectionEnd, 3)),
      },
    ]

    const state = EditorState.create({
      doc: initialValueRef.current,
      extensions: [
        themeCompartment.of(oneDark),
        markdown(),
        autocompletion({ override: [internalLinkCompletionSource] }),
        history(),
        keymap.of([...customShortcuts, indentWithTab, ...historyKeymap, ...defaultKeymap]),
        EditorView.lineWrapping,
        updateListener,
        EditorView.theme({
          '&': {
            height: '100%',
            backgroundColor: 'hsl(var(--surface-alt))',
            fontSize: '13px',
            color: 'hsl(var(--foreground))',
          },
          '.cm-editor': { height: '100%' },
          '.cm-scroller': { height: '100%' },
          '&.cm-focused': { outline: 'none' },
        }),
      ],
    })

    const view = new EditorView({
      state,
      parent: editorRef.current,
    })

    viewRef.current = view
    editorViewRef.current = view

    const focusFrame = requestAnimationFrame(() => {
      view.focus()
    })

    return () => {
      cancelAnimationFrame(focusFrame)
      view.destroy()
      viewRef.current = null
      editorViewRef.current = null
    }
  }, [editorViewRef, onCursorLineChange, themeCompartment])

  useEffect(() => {
    const view = viewRef.current
    if (!view) {
      return
    }
    const currentDoc = view.state.doc.toString()
    if (currentDoc !== value) {
      view.dispatch({
        changes: { from: 0, to: currentDoc.length, insert: value },
      })
    }
  }, [value])

  return <div ref={editorRef} className="markdown-code-editor" onPaste={onPaste} />
}
