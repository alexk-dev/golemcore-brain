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

import type { ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTreeStore } from '../../stores/tree'
import { useSpaceStore } from '../../stores/space'

interface SpaceSwitcherProps {
  className?: string
}

export function SpaceSwitcher({ className }: SpaceSwitcherProps) {
  const spaces = useSpaceStore((state) => state.spaces)
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const setActiveSlug = useSpaceStore((state) => state.setActiveSlug)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const navigate = useNavigate()

  if (spaces.length === 0) {
    return null
  }

  const handleChange = async (event: ChangeEvent<HTMLSelectElement>) => {
    const nextSlug = event.target.value
    setActiveSlug(nextSlug)
    navigate('/')
    await reloadTree()
  }

  return (
    <select
      className={className ?? 'action-button-secondary'}
      value={activeSlug}
      onChange={handleChange}
      aria-label="Active space"
      title="Active space"
    >
      {spaces.map((space) => (
        <option key={space.id} value={space.slug}>
          {space.name}
        </option>
      ))}
    </select>
  )
}
