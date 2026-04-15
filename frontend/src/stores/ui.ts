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
import type { AuthConfig, PublicUserView } from '../types'

interface UiState {
  isDark: boolean
  sidebarVisible: boolean
  searchOpen: boolean
  quickSwitcherOpen: boolean
  authDisabled: boolean
  publicAccess: boolean
  currentUser: PublicUserView | null
  authResolved: boolean
  setSidebarVisible: (value: boolean) => void
  toggleSidebar: () => void
  setSearchOpen: (value: boolean) => void
  setQuickSwitcherOpen: (value: boolean) => void
  setAuthConfig: (config: AuthConfig) => void
  setCurrentUser: (user: PublicUserView | null) => void
}

export const useUiStore = create<UiState>((set) => ({
  isDark: true,
  sidebarVisible: typeof window !== 'undefined'
    ? window.matchMedia('(min-width: 768px)').matches
    : true,
  searchOpen: false,
  quickSwitcherOpen: false,
  authDisabled: false,
  publicAccess: false,
  currentUser: null,
  authResolved: false,
  setSidebarVisible: (value) => set({ sidebarVisible: value }),
  toggleSidebar: () => set((state) => ({ sidebarVisible: !state.sidebarVisible })),
  setSearchOpen: (value) => set({ searchOpen: value }),
  setQuickSwitcherOpen: (value) => set({ quickSwitcherOpen: value }),
  setAuthConfig: (config) => set({
    authDisabled: config.authDisabled,
    publicAccess: config.publicAccess,
    currentUser: config.user,
    authResolved: true,
  }),
  setCurrentUser: (user) => set({ currentUser: user }),
}))
