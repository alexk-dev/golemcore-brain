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

import { PageConflictError, getPageByPath, updatePage } from '../lib/api'
import type { WikiPage } from '../types'

interface EditorState {
  page: WikiPage | null
  initialPage: WikiPage | null
  title: string
  slug: string
  content: string
  loading: boolean
  error: string | null
  conflict: WikiPage | null
  loadPageData: (path: string) => Promise<void>
  savePage: () => Promise<WikiPage | null>
  reloadFromConflict: () => void
  mergeConflictWithLocalDraft: () => void
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
  conflict: null,
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
        conflict: null,
      })
    } catch (error) {
      set({ error: (error as Error).message, page: null, conflict: null })
    } finally {
      set({ loading: false })
    }
  },
  savePage: async () => {
    const { page, title, slug, content, initialPage } = get()
    if (!page) {
      return null
    }
    set({ loading: true, error: null })
    try {
      const updatedPage = await updatePage(page.path, {
        title,
        slug,
        content,
        revision: initialPage?.revision,
      })
      set({
        page: updatedPage,
        initialPage: { ...updatedPage },
        title: updatedPage.title,
        slug: updatedPage.slug,
        content: updatedPage.content,
        conflict: null,
      })
      return updatedPage
    } catch (error) {
      if (error instanceof PageConflictError) {
        set({ error: error.message, conflict: error.currentPage })
      } else {
        set({ error: (error as Error).message })
      }
      throw error
    } finally {
      set({ loading: false })
    }
  },
  reloadFromConflict: () =>
    set((state) => {
      if (!state.conflict) {
        return state
      }
      return {
        page: state.conflict,
        initialPage: { ...state.conflict },
        title: state.conflict.title,
        slug: state.conflict.slug,
        content: state.conflict.content,
        error: null,
        conflict: null,
      }
    }),
  mergeConflictWithLocalDraft: () =>
    set((state) => {
      if (!state.conflict) {
        return state
      }
      return {
        page: state.conflict,
        initialPage: { ...state.conflict },
        error: null,
        conflict: null,
      }
    }),
  setTitle: (value) => set({ title: value }),
  setSlug: (value) => set({ slug: value }),
  setContent: (value) => set({ content: value }),
}))
