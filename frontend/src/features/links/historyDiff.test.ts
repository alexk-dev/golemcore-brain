import { describe, expect, it } from 'vitest'

import { buildLineDiff } from './historyDiff'

describe('buildLineDiff', () => {
  it('produces added, removed, and unchanged lines', () => {
    expect(buildLineDiff('alpha\nbeta', 'alpha\ngamma')).toEqual([
      { type: 'unchanged', text: 'alpha' },
      { type: 'removed', text: 'beta' },
      { type: 'added', text: 'gamma' },
    ])
  })
})
