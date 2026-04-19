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

import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import {
  BrainCircuit,
  Check,
  ChevronDown,
  KeyRound,
  Layers,
  LogOut,
  Shield,
  UserRound,
  Users as UsersIcon,
} from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

import { useSpaceStore } from '../../stores/space'
import { useTreeStore } from '../../stores/tree'

interface UserMenuProps {
  username: string
  canAccessAccount: boolean
  canManageUsers: boolean
  onLogout: () => void
}

export function UserMenu({ username, canAccessAccount, canManageUsers, onLogout }: UserMenuProps) {
  const spaces = useSpaceStore((state) => state.spaces)
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const setActiveSlug = useSpaceStore((state) => state.setActiveSlug)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const navigate = useNavigate()

  const showSpacesGroup = spaces.length > 1

  const handleSpaceChange = (slug: string) => {
    if (!slug || slug === activeSlug) {
      return
    }
    setActiveSlug(slug)
    navigate('/')
    void reloadTree()
  }

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          className="action-button-secondary"
          aria-label={`Account menu for ${username}`}
          title={username}
        >
          <UserRound size={16} />
          <span className="hidden md:inline">{username}</span>
          <ChevronDown size={14} className="hidden md:inline" aria-hidden="true" />
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="user-menu__content"
        >
          <div className="user-menu__header">
            <div className="user-menu__header-label">Signed in as</div>
            <div className="user-menu__header-name">{username}</div>
          </div>
          {showSpacesGroup ? (
            <>
              <DropdownMenu.Separator className="user-menu__separator" />
              <DropdownMenu.Label className="user-menu__label">Switch space</DropdownMenu.Label>
              <DropdownMenu.RadioGroup
                aria-label="Switch space"
                value={activeSlug}
                onValueChange={handleSpaceChange}
              >
                {spaces.map((space) => (
                  <DropdownMenu.RadioItem
                    key={space.id}
                    value={space.slug}
                    className="user-menu__item user-menu__item--radio"
                  >
                    <Layers size={14} aria-hidden="true" />
                    <span className="user-menu__item-label">{space.name}</span>
                    <DropdownMenu.ItemIndicator className="user-menu__item-indicator">
                      <Check size={14} aria-hidden="true" />
                    </DropdownMenu.ItemIndicator>
                  </DropdownMenu.RadioItem>
                ))}
              </DropdownMenu.RadioGroup>
            </>
          ) : null}
          <DropdownMenu.Separator className="user-menu__separator" />
          {canAccessAccount ? (
            <DropdownMenu.Item asChild>
              <Link to="/account" className="user-menu__item">
                <UserRound size={14} />
                <span>Account</span>
              </Link>
            </DropdownMenu.Item>
          ) : null}
          {canManageUsers ? (
            <>
              <DropdownMenu.Separator className="user-menu__separator" />
              <DropdownMenu.Label className="user-menu__label">Administration</DropdownMenu.Label>
              <DropdownMenu.Item asChild>
                <Link to="/users" className="user-menu__item">
                  <UsersIcon size={14} />
                  <span>Users</span>
                </Link>
              </DropdownMenu.Item>
              <DropdownMenu.Item asChild>
                <Link to="/spaces" className="user-menu__item">
                  <Shield size={14} />
                  <span>Manage spaces</span>
                </Link>
              </DropdownMenu.Item>
              <DropdownMenu.Item asChild>
                <Link to="/api-keys" className="user-menu__item">
                  <KeyRound size={14} />
                  <span>API Keys</span>
                </Link>
              </DropdownMenu.Item>
              <DropdownMenu.Item asChild>
                <Link to="/llm-settings" className="user-menu__item">
                  <BrainCircuit size={14} />
                  <span>AI Models</span>
                </Link>
              </DropdownMenu.Item>
            </>
          ) : null}
          <DropdownMenu.Separator className="user-menu__separator" />
          <DropdownMenu.Item
            className="user-menu__item user-menu__item--danger"
            onSelect={(event) => {
              event.preventDefault()
              onLogout()
            }}
          >
            <LogOut size={14} />
            <span>Logout</span>
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
