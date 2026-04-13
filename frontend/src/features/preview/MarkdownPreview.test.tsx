import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { setCurrentSpaceSlug } from '../../lib/api'
import { MarkdownPreview } from './MarkdownPreview'

vi.mock('../../lib/basePath', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/basePath')>()
  return {
    ...actual,
    appBasePath: '/brain',
    isAppApiPath: (path: string, basePath = '/brain') => actual.isAppApiPath(path, basePath),
    stripAppBasePath: (path: string, basePath = '/brain') => actual.stripAppBasePath(path, basePath),
    withAppBasePath: (path: string, basePath = '/brain') => actual.withAppBasePath(path, basePath),
  }
})

describe('MarkdownPreview', () => {
  it('renders internal links as router links and code blocks with copy affordance', () => {
    render(
      <MemoryRouter>
        <MarkdownPreview
          content={'[Docs](../shared/docs)\n\n```js\nconsole.log(1)\n```'}
          path="guides/runbook"
          darkMode={false}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Docs' })).toHaveAttribute('href', '/shared/docs')
    expect(screen.getByTestId('markdown-code-copy-button')).toBeInTheDocument()
  })

  it('normalizes legacy local asset image URLs and cache-busts them when assets change', () => {
    setCurrentSpaceSlug('default')

    const { container } = render(
      <MemoryRouter>
        <MarkdownPreview
          content={'![Diagram](/api/assets?path=docs/page&name=image.png)\n\n<audio controls src="/api/assets?path=docs/page&name=audio.mp3"></audio>\n\n![Remote](https://example.com/image.png)'}
          path="docs/page"
          darkMode={false}
          assetVersion={12345}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('img', { name: 'Diagram' })).toHaveAttribute(
      'src',
      '/brain/api/spaces/default/assets?path=docs/page&name=image.png&v=12345',
    )
    expect(container.querySelector('audio')?.getAttribute('src')).toBe(
      '/brain/api/spaces/default/assets?path=docs/page&name=audio.mp3&v=12345',
    )
    expect(screen.getByRole('img', { name: 'Remote' })).toHaveAttribute('src', 'https://example.com/image.png')
  })
})
