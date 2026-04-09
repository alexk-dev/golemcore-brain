import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { MarkdownLink } from './MarkdownLink'

describe('MarkdownLink', () => {
  it('resolves relative wiki links against the current path', () => {
    render(
      <MemoryRouter>
        <MarkdownLink href="../shared/guide" currentPath="docs/runbook">
          Shared guide
        </MarkdownLink>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Shared guide' })).toHaveAttribute('href', '/shared/guide')
  })
})
