import { create } from 'zustand'

import { getPageByPath, updatePage } from '../lib/api'
import type { WikiPage } from '../types'

interface EditorState {
  page: WikiPage | null
  initialPage: WikiPage | null
  title: string
  slug: string
  content: string
  loading: boolean
  error: string | null
  loadPageData: (path: string) => Promise<void>
  savePage: () => Promise<WikiPage | null>
  setTitle: (value: string) => void
  setSlug: (value: string) => void
  setContent: (value: string) => void
}

export const useEditorStore = create<EditorState>((set, get) => ({
  page: null,
  initialPage: null,
  title: '',
  slug: '',
  content: '',
  loading: false,
  error: null,
  loadPageData: async (path) => {
    set({ loading: true, error: null })
    try {
      const page = await getPageByPath(path)
      set({
        page,
        initialPage: { ...page },
        title: page.title,
        slug: page.slug,
        content: page.content,
      })
    } catch (error) {
      set({ error: (error as Error).message, page: null })
    } finally {
      set({ loading: false })
    }
  },
  savePage: async () => {
    const { page, title, slug, content } = get()
    if (!page) {
      return null
    }
    set({ loading: true, error: null })
    try {
      const updatedPage = await updatePage(page.path, { title, slug, content })
      set({
        page: updatedPage,
        initialPage: { ...updatedPage },
        title: updatedPage.title,
        slug: updatedPage.slug,
        content: updatedPage.content,
      })
      return updatedPage
    } catch (error) {
      set({ error: (error as Error).message })
      throw error
    } finally {
      set({ loading: false })
    }
  },
  setTitle: (value) => set({ title: value }),
  setSlug: (value) => set({ slug: value }),
  setContent: (value) => set({ content: value }),
}))
