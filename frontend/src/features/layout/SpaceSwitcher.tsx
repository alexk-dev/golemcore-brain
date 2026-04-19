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
import { Check, ChevronDown, Layers } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useSpaceStore } from '../../stores/space'
import { useTreeStore } from '../../stores/tree'

export function SpaceSwitcher() {
  const spaces = useSpaceStore((state) => state.spaces)
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const setActiveSlug = useSpaceStore((state) => state.setActiveSlug)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const navigate = useNavigate()

  if (spaces.length <= 1) {
    return null
  }

  const activeSpace = spaces.find((space) => space.slug === activeSlug) ?? spaces[0]
  const activeName = activeSpace?.name ?? activeSlug

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
          className="space-switcher__trigger action-button-secondary"
          aria-label={`Switch space, current space ${activeName}`}
          title={`Switch space: ${activeName}`}
        >
          <Layers size={16} aria-hidden="true" />
          <span className="space-switcher__trigger-label">{activeName}</span>
          <ChevronDown size={14} className="space-switcher__trigger-chevron" aria-hidden="true" />
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align="end" sideOffset={6} className="space-switcher__content">
          <DropdownMenu.Label className="space-switcher__label">Switch space</DropdownMenu.Label>
          <DropdownMenu.RadioGroup
            aria-label="Switch space"
            value={activeSlug}
            onValueChange={handleSpaceChange}
          >
            {spaces.map((space) => (
              <DropdownMenu.RadioItem
                key={space.id}
                value={space.slug}
                className="space-switcher__item space-switcher__item--radio"
              >
                <Layers size={14} aria-hidden="true" />
                <span className="space-switcher__item-label">{space.name}</span>
                <DropdownMenu.ItemIndicator className="space-switcher__item-indicator">
                  <Check size={14} aria-hidden="true" />
                </DropdownMenu.ItemIndicator>
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
