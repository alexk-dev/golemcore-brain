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

export interface HistoryDiffLine {
  type: 'added' | 'removed' | 'unchanged'
  text: string
}

export function buildLineDiff(previousText: string, nextText: string): HistoryDiffLine[] {
  const previousLines = previousText.split('\n')
  const nextLines = nextText.split('\n')
  const lcs = Array.from({ length: previousLines.length + 1 }, () =>
    Array<number>(nextLines.length + 1).fill(0),
  )

  for (let left = previousLines.length - 1; left >= 0; left -= 1) {
    for (let right = nextLines.length - 1; right >= 0; right -= 1) {
      if (previousLines[left] === nextLines[right]) {
        lcs[left][right] = lcs[left + 1][right + 1] + 1
      } else {
        lcs[left][right] = Math.max(lcs[left + 1][right], lcs[left][right + 1])
      }
    }
  }

  const lines: HistoryDiffLine[] = []
  let left = 0
  let right = 0
  while (left < previousLines.length && right < nextLines.length) {
    if (previousLines[left] === nextLines[right]) {
      lines.push({ type: 'unchanged', text: previousLines[left] })
      left += 1
      right += 1
      continue
    }
    if (lcs[left + 1][right] >= lcs[left][right + 1]) {
      lines.push({ type: 'removed', text: previousLines[left] })
      left += 1
    } else {
      lines.push({ type: 'added', text: nextLines[right] })
      right += 1
    }
  }

  while (left < previousLines.length) {
    lines.push({ type: 'removed', text: previousLines[left] })
    left += 1
  }

  while (right < nextLines.length) {
    lines.push({ type: 'added', text: nextLines[right] })
    right += 1
  }

  return lines
}
