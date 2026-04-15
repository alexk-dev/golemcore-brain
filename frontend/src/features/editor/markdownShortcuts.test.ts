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

  it('keeps selected text when applying heading markers', () => {
    expect(applyHeadingToSelection('intro\nselected title\noutro', 6, 20, 1)).toEqual({
      text: 'intro\n# selected title\noutro',
      selectionStart: 6,
      selectionEnd: 22,
    })
  })

  it('replaces an existing heading marker on the selected line', () => {
    expect(applyHeadingToSelection('intro\n### selected title\noutro', 10, 24, 1)).toEqual({
      text: 'intro\n# selected title\noutro',
      selectionStart: 6,
      selectionEnd: 22,
    })
  })
})
