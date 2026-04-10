import { describe, expect, it } from 'vitest'

import { applyHeadingToSelection, wrapSelectionText } from './markdownShortcuts'

describe('markdownShortcuts', () => {
  it('wraps the current selection', () => {
    expect(wrapSelectionText('hello', 0, 5, '**')).toEqual({
      text: '**hello**',
      selectionStart: 2,
      selectionEnd: 7,
    })
  })

  it('applies heading markers to the selected line', () => {
    expect(applyHeadingToSelection('hello\nworld', 0, 5, 2)).toEqual({
      text: '## hello\nworld',
      selectionStart: 0,
      selectionEnd: 8,
    })
  })
})
