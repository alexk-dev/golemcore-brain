import { create } from 'zustand'

import { getLinkStatus, getPageByPath, getPageHistory } from '../lib/api'
import type { WikiLinkStatus, WikiPage, WikiPageHistoryEntry } from '../types'

interface ViewerState {
  page: WikiPage | null
  linkStatus: WikiLinkStatus | null
  history: WikiPageHistoryEntry[]
  loading: boolean
  error: string | null
  loadPageData: (path: string) => Promise<void>
  refreshPageData: (path: string) => Promise<void>
}

async function fetchViewerData(path: string) {
  const page = await getPageByPath(path)
  const linkStatus = await getLinkStatus(path).catch(() => null)
  const history = await getPageHistory(path).catch(() => [])
  return { page, linkStatus, history }
}

export const useViewerStore = create<ViewerState>((set) => ({
  page: null,
  linkStatus: null,
  history: [],
  loading: false,
  error: null,
  loadPageData: async (path) => {
    set({ loading: true, error: null })
    try {
      const { page, linkStatus, history } = await fetchViewerData(path)
      set({ page, linkStatus, history })
    } catch (error) {
      set({ error: (error as Error).message, page: null, linkStatus: null, history: [] })
    } finally {
      set({ loading: false })
    }
  },
  refreshPageData: async (path) => {
    try {
      const { page, linkStatus, history } = await fetchViewerData(path)
      set({ page, linkStatus, history, error: null })
    } catch (error) {
      set({ error: (error as Error).message })
    }
  },
}))
