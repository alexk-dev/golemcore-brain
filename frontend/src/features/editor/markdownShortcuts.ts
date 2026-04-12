import type { EditorView } from '@codemirror/view'

export interface TextTransformResult {
  text: string
  selectionStart: number
  selectionEnd: number
}

export type TextTransform = (text: string, selectionStart: number, selectionEnd: number) => TextTransformResult

export function applyTextTransform(view: EditorView, transform: TextTransform): void {
  const selection = view.state.selection.main
  const result = transform(view.state.doc.toString(), selection.from, selection.to)
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: result.text },
    selection: { anchor: result.selectionStart, head: result.selectionEnd },
  })
}

export function wrapSelectionText(
  text: string,
  selectionStart: number,
  selectionEnd: number,
  before: string,
  after = before,
): TextTransformResult {
  const selectedText = text.slice(selectionStart, selectionEnd)
  const nextText = `${text.slice(0, selectionStart)}${before}${selectedText}${after}${text.slice(selectionEnd)}`
  return {
    text: nextText,
    selectionStart: selectionStart + before.length,
    selectionEnd: selectionStart + before.length + selectedText.length,
  }
}

export function applyHeadingToSelection(
  text: string,
  selectionStart: number,
  selectionEnd: number,
  level: 1 | 2 | 3,
): TextTransformResult {
  const linePrefix = `${'#'.repeat(level)} `
  const blockStart = text.lastIndexOf('\n', Math.max(selectionStart - 1, 0))
  const start = blockStart === -1 ? 0 : blockStart + 1
  const blockEndCandidate = text.indexOf('\n', selectionEnd)
  const end = blockEndCandidate === -1 ? text.length : blockEndCandidate
  const block = text.slice(start, end)
  const lines = block.split('\n')
  const updatedLines = lines.map((line) => {
    if (!line.trim()) {
      return line
    }
    return `${linePrefix}${line.replace(/^#{1,6}\s+/, '')}`
  })
  const replacement = updatedLines.join('\n')
  const nextText = `${text.slice(0, start)}${replacement}${text.slice(end)}`
  return {
    text: nextText,
    selectionStart: start,
    selectionEnd: start + replacement.length,
  }
}
