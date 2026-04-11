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
  setDarkMode: (value: boolean) => void
  toggleDarkMode: () => void
  setSidebarVisible: (value: boolean) => void
  toggleSidebar: () => void
  setSearchOpen: (value: boolean) => void
  setQuickSwitcherOpen: (value: boolean) => void
  setAuthConfig: (config: AuthConfig) => void
  setCurrentUser: (user: PublicUserView | null) => void
}

export const useUiStore = create<UiState>((set) => ({
  isDark: typeof window !== 'undefined'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false,
  sidebarVisible: typeof window !== 'undefined'
    ? window.matchMedia('(min-width: 768px)').matches
    : true,
  searchOpen: false,
  quickSwitcherOpen: false,
  authDisabled: false,
  publicAccess: false,
  currentUser: null,
  authResolved: false,
  setDarkMode: (value) => set({ isDark: value }),
  toggleDarkMode: () => set((state) => ({ isDark: !state.isDark })),
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
