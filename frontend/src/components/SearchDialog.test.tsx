import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { WikiTreeNode } from '../types'
import { SearchDialog } from './SearchDialog'

vi.mock('../lib/api', () => ({
  searchPages: vi.fn(async () => [
    {
      id: 'guides/runbook',
      path: 'guides/runbook',
      title: 'Runbook',
      excerpt: 'Runbook excerpt',
      parentPath: 'guides',
      kind: 'PAGE',
    },
    {
      id: 'guides/setup',
      path: 'guides/setup',
      title: 'Setup',
      excerpt: 'Setup excerpt',
      parentPath: 'guides',
      kind: 'PAGE',
    },
  ]),
}))

const tree: WikiTreeNode = {
  id: 'root',
  path: '',
  parentPath: null,
  title: 'Welcome',
  slug: '',
  kind: 'ROOT',
  hasChildren: true,
  children: [],
}

describe('SearchDialog', () => {
  it('supports keyboard navigation and enter selection', async () => {
    const navigated: string[] = []

    render(
      <SearchDialog
        open={true}
        tree={tree}
        onOpenChange={() => undefined}
        onNavigate={(path) => navigated.push(path)}
      />,
    )

    const input = screen.getByPlaceholderText('Search documentation, runbooks, and notes')
    fireEvent.change(input, { target: { value: 'run' } })

    await waitFor(() => {
      expect(screen.getByText('Runbook')).toBeInTheDocument()
    })

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(navigated.length).toBe(1)
  })
})
