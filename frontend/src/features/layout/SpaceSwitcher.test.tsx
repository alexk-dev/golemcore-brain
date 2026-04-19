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

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useSpaceStore } from '../../stores/space'
import { useTreeStore } from '../../stores/tree'
import type { Space } from '../../types'
import { SpaceSwitcher } from './SpaceSwitcher'

const spaces: Space[] = [
  { id: 's1', slug: 'default', name: 'Default', createdAt: '2026-01-01T00:00:00Z' },
  { id: 's2', slug: 'product', name: 'Product', createdAt: '2026-01-01T00:00:00Z' },
]

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

function renderSwitcher() {
  return render(
    <MemoryRouter initialEntries={['/docs/page']}>
      <SpaceSwitcher />
      <Routes>
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SpaceSwitcher', () => {
  beforeEach(() => {
    useSpaceStore.setState({
      spaces,
      activeSlug: 'default',
      loaded: true,
    })
    useTreeStore.setState({ reloadTree: vi.fn(async () => undefined) })
  })

  it('switches the active space, reloads the tree, and navigates home', async () => {
    const reloadTree = vi.fn(async () => undefined)
    useTreeStore.setState({ reloadTree })
    const user = userEvent.setup()

    renderSwitcher()
    await user.click(screen.getByRole('button', { name: 'Switch space, current space Default' }))
    await user.click(await screen.findByRole('menuitemradio', { name: 'Product' }))

    await waitFor(() => {
      expect(useSpaceStore.getState().activeSlug).toBe('product')
    })
    expect(reloadTree).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('location')).toHaveTextContent('/')
  })

  it('stays hidden when there is only one space', () => {
    useSpaceStore.setState({ spaces: [spaces[0]], activeSlug: 'default', loaded: true })

    renderSwitcher()

    expect(screen.queryByRole('button', { name: /Switch space/i })).not.toBeInTheDocument()
  })
})
