import { autocompletion } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { markdown } from '@codemirror/lang-markdown'
import { Compartment, EditorState } from '@codemirror/state'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorView, keymap } from '@codemirror/view'
import { githubLight } from '@fsegurai/codemirror-theme-github-light'
import { useEffect, useRef, useState } from 'react'

interface MarkdownCodeEditorProps {
  value: string
  darkMode: boolean
  onChange: (value: string) => void
  editorViewRef: React.RefObject<EditorView | null>
}

export function MarkdownCodeEditor({
  value,
  darkMode,
  onChange,
  editorViewRef,
}: MarkdownCodeEditorProps) {
  const editorRef = useRef<HTMLDivElement | null>(null)
  const viewRef = useRef<EditorView | null>(null)
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
    })

    const state = EditorState.create({
      doc: value,
      extensions: [
        themeCompartment.of(darkMode ? oneDark : githubLight),
        markdown(),
        autocompletion(),
        history(),
        keymap.of([indentWithTab, ...historyKeymap, ...defaultKeymap]),
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

    return () => {
      view.destroy()
      viewRef.current = null
      editorViewRef.current = null
    }
  }, [darkMode, editorViewRef, themeCompartment, value])

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

  useEffect(() => {
    const view = viewRef.current
    if (!view) {
      return
    }
    view.dispatch({
      effects: themeCompartment.reconfigure(darkMode ? oneDark : githubLight),
    })
  }, [darkMode, themeCompartment])

  return <div ref={editorRef} className="markdown-code-editor" />
}
