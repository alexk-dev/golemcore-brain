import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { MarkdownPreview } from './MarkdownPreview'

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

  it('cache-busts local asset images when assets change', () => {
    render(
      <MemoryRouter>
        <MarkdownPreview
          content={'![Diagram](/api/assets?path=docs/page&name=image.png)\n\n![Remote](https://example.com/image.png)'}
          path="docs/page"
          darkMode={false}
          assetVersion={12345}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('img', { name: 'Diagram' })).toHaveAttribute(
      'src',
      '/api/assets?path=docs/page&name=image.png&v=12345',
    )
    expect(screen.getByRole('img', { name: 'Remote' })).toHaveAttribute('src', 'https://example.com/image.png')
  })
})
