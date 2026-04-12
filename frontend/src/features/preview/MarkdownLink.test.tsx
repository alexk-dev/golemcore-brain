import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { setCurrentSpaceSlug } from '../../lib/api'
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

  it('normalizes legacy local asset links to the active space API route', () => {
    setCurrentSpaceSlug('default')

    render(
      <MemoryRouter>
        <MarkdownLink href="/api/assets?path=docs/runbook&name=diagram.png" currentPath="docs/runbook">
          Diagram
        </MarkdownLink>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Diagram' })).toHaveAttribute(
      'href',
      '/api/spaces/default/assets?path=docs/runbook&name=diagram.png',
    )
  })
})
