import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'

import { NotFoundPage } from './NotFoundPage'

describe('NotFoundPage', () => {
  it('invokes create callback', () => {
    let clicked = 0
    render(<MemoryRouter><NotFoundPage path="docs/missing" onCreate={() => { clicked += 1 }} /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: /Create page by path/i }))
    expect(clicked).toBe(1)
  })
})
