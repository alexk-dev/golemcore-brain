import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { setCurrentSpaceSlug } from '../../lib/api'
import { MarkdownLink } from './MarkdownLink'

vi.mock('../../lib/basePath', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/basePath')>()
  return {
    ...actual,
    appBasePath: '/brain',
    isAppApiPath: (path: string, basePath = '/brain') => actual.isAppApiPath(path, basePath),
    withAppBasePath: (path: string, basePath = '/brain') => actual.withAppBasePath(path, basePath),
  }
})

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
      '/brain/api/spaces/default/assets?path=docs/runbook&name=diagram.png',
    )
  })
})
