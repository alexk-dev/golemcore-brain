/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

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
