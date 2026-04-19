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

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { UserMenu } from './UserMenu'
import { useSpaceStore } from '../../stores/space'
import { useTreeStore } from '../../stores/tree'
import type { Space } from '../../types'

const spaces: Space[] = [
  { id: 's1', slug: 'default', name: 'Default', createdAt: '2026-01-01T00:00:00Z' },
  { id: 's2', slug: 'product', name: 'Product', createdAt: '2026-01-01T00:00:00Z' },
  { id: 's3', slug: 'eng', name: 'Engineering', createdAt: '2026-01-01T00:00:00Z' },
]

function setSpaceStore(override: Partial<ReturnType<typeof useSpaceStore.getState>> = {}) {
  useSpaceStore.setState({
    spaces,
    activeSlug: 'default',
    loaded: true,
    ...override,
  })
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

function renderMenu(props: Partial<Parameters<typeof UserMenu>[0]> = {}) {
  return render(
    <MemoryRouter initialEntries={['/some/page']}>
      <UserMenu
        username="admin"
        canAccessAccount
        canManageUsers
        onLogout={() => {}}
        {...props}
      />
      <Routes>
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /Account menu/ }))
}

describe('UserMenu — space switching', () => {
  beforeEach(() => {
    setSpaceStore()
    useTreeStore.setState({ reloadTree: vi.fn(async () => undefined) })
  })

  it('renders every space as a selectable item under a Switch space group', async () => {
    const user = userEvent.setup()
    renderMenu()
    await openMenu(user)

    const group = await screen.findByRole('group', { name: /Switch space/i })
    for (const space of spaces) {
      expect(within(group).getByRole('menuitemradio', { name: space.name })).toBeInTheDocument()
    }
  })

  it('marks the active space as checked', async () => {
    const user = userEvent.setup()
    renderMenu()
    await openMenu(user)

    const active = await screen.findByRole('menuitemradio', { name: 'Default' })
    expect(active).toHaveAttribute('aria-checked', 'true')
    const other = screen.getByRole('menuitemradio', { name: 'Product' })
    expect(other).toHaveAttribute('aria-checked', 'false')
  })

  it('hides the spaces group when there is only one space', async () => {
    setSpaceStore({ spaces: [spaces[0]], activeSlug: 'default' })
    const user = userEvent.setup()
    renderMenu()
    await openMenu(user)

    // Menu content present but without the group
    await screen.findByRole('menu')
    expect(screen.queryByRole('group', { name: /Switch space/i })).not.toBeInTheDocument()
  })

  it('hides the spaces group when there are no spaces', async () => {
    setSpaceStore({ spaces: [], activeSlug: 'default' })
    const user = userEvent.setup()
    renderMenu()
    await openMenu(user)

    await screen.findByRole('menu')
    expect(screen.queryByRole('group', { name: /Switch space/i })).not.toBeInTheDocument()
  })

  it('switches the active space, reloads the tree, and navigates to "/"', async () => {
    const reloadTree = vi.fn(async () => undefined)
    useTreeStore.setState({ reloadTree })
    const user = userEvent.setup()

    renderMenu()
    await openMenu(user)
    await user.click(await screen.findByRole('menuitemradio', { name: 'Product' }))

    await waitFor(() => {
      expect(useSpaceStore.getState().activeSlug).toBe('product')
    })
    expect(reloadTree).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('location').textContent).toBe('/')
  })

  it('labels the admin link as "Manage spaces" to avoid confusion with switching', async () => {
    const user = userEvent.setup()
    renderMenu()
    await openMenu(user)

    // The administration section link points at /spaces and must be relabeled
    // so it is not confused with the per-user active-space switcher above.
    const manageLink = await screen.findByRole('menuitem', { name: /Manage spaces/i })
    expect(manageLink).toHaveAttribute('href', '/spaces')
    expect(screen.queryByRole('menuitem', { name: /^Spaces$/ })).not.toBeInTheDocument()
  })

  it('keeps the switcher available to non-admins', async () => {
    const user = userEvent.setup()
    renderMenu({ canManageUsers: false })
    await openMenu(user)

    expect(await screen.findByRole('group', { name: /Switch space/i })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Manage spaces/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Users/i })).not.toBeInTheDocument()
  })
})
