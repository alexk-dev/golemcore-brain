import { create } from 'zustand'

interface UiState {
  isDark: boolean
  sidebarVisible: boolean
  searchOpen: boolean
  quickSwitcherOpen: boolean
  setDarkMode: (value: boolean) => void
  toggleDarkMode: () => void
  setSidebarVisible: (value: boolean) => void
  toggleSidebar: () => void
  setSearchOpen: (value: boolean) => void
  setQuickSwitcherOpen: (value: boolean) => void
}

export const useUiStore = create<UiState>((set) => ({
  isDark: typeof window !== 'undefined'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false,
  sidebarVisible: true,
  searchOpen: false,
  quickSwitcherOpen: false,
  setDarkMode: (value) => set({ isDark: value }),
  toggleDarkMode: () => set((state) => ({ isDark: !state.isDark })),
  setSidebarVisible: (value) => set({ sidebarVisible: value }),
  toggleSidebar: () => set((state) => ({ sidebarVisible: !state.sidebarVisible })),
  setSearchOpen: (value) => set({ searchOpen: value }),
  setQuickSwitcherOpen: (value) => set({ quickSwitcherOpen: value }),
}))
