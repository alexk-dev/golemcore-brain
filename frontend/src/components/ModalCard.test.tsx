import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ModalCard } from './ModalCard'

describe('ModalCard', () => {
  it('keeps long dialogs usable within the viewport', () => {
    render(
      <ModalCard
        open
        title="Configure model"
        description="Long forms must remain reachable on small screens."
        onOpenChange={vi.fn()}
        footer={<button type="button">Save</button>}
      >
        <div>Dialog body</div>
      </ModalCard>,
    )

    expect(screen.getByRole('dialog', { name: 'Configure model' })).toHaveClass(
      'max-h-[calc(100dvh-2rem)]',
      'overflow-y-auto',
    )
    expect(screen.getByText('Save').parentElement).toHaveClass('flex-wrap')
    expect(screen.getByRole('button', { name: 'Close dialog' })).toBeInTheDocument()
  })
})
