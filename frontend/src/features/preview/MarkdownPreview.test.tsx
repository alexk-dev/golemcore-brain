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
})
