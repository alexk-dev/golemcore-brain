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

import { create } from 'zustand'
import * as api from '../lib/api'
import type { Space } from '../types'

function applySlug(slug: string) {
  try {
    const fn = (api as Record<string, unknown>).setCurrentSpaceSlug
    if (typeof fn === 'function') {
      ;(fn as (value: string) => void)(slug)
    }
  } catch {
    /* mock modules may omit this export */
  }
}

async function fetchSpaces(): Promise<Space[]> {
  try {
    const fn = (api as Record<string, unknown>).listSpaces
    if (typeof fn !== 'function') {
      return []
    }
    return await (fn as () => Promise<Space[]>)()
  } catch {
    return []
  }
}

const STORAGE_KEY = 'golemcore-brain:active-space-slug'

function readStoredSlug(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    return window.localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeStoredSlug(slug: string) {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, slug)
  } catch {
    /* ignore */
  }
}

interface SpaceState {
  spaces: Space[]
  activeSlug: string
  loaded: boolean
  reloadSpaces: () => Promise<void>
  setActiveSlug: (slug: string) => void
}

const initialSlug = readStoredSlug() ?? 'default'
applySlug(initialSlug)

export const useSpaceStore = create<SpaceState>((set, get) => ({
  spaces: [],
  activeSlug: initialSlug,
  loaded: false,
  reloadSpaces: async () => {
    const spaces = await fetchSpaces()
    const current = get().activeSlug
    const stillExists = spaces.some((space) => space.slug === current)
    const nextSlug = stillExists ? current : spaces[0]?.slug ?? 'default'
    applySlug(nextSlug)
    writeStoredSlug(nextSlug)
    set({ spaces, activeSlug: nextSlug, loaded: true })
  },
  setActiveSlug: (slug) => {
    applySlug(slug)
    writeStoredSlug(slug)
    set({ activeSlug: slug })
  },
}))
