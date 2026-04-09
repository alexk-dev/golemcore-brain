import { create } from 'zustand'

import { getTree } from '../lib/api'
import type { WikiTreeNode } from '../types'

interface TreeState {
  tree: WikiTreeNode | null
  loading: boolean
  error: string | null
  activeNodeId: string | null
  openNodeIdSet: Record<string, true>
  byPath: Record<string, WikiTreeNode>
  byId: Record<string, WikiTreeNode>
  flatPages: WikiTreeNode[]
  reloadTree: () => Promise<void>
  toggleNode: (id: string) => void
  openNode: (id: string) => void
  closeNode: (id: string) => void
  setActiveNodeId: (id: string | null) => void
  getPageByPath: (path: string) => WikiTreeNode | null
  getPageById: (id: string) => WikiTreeNode | null
  openAncestorsForPath: (path: string) => void
}

function buildIndexes(root: WikiTreeNode) {
  const byPath: Record<string, WikiTreeNode> = {}
  const byId: Record<string, WikiTreeNode> = {}
  const flatPages: WikiTreeNode[] = []

  const walk = (node: WikiTreeNode) => {
    byId[node.id] = node
    byPath[node.path] = node
    flatPages.push(node)
    node.children.forEach(walk)
  }

  walk(root)
  return { byPath, byId, flatPages }
}

export const useTreeStore = create<TreeState>((set, get) => ({
  tree: null,
  loading: false,
  error: null,
  activeNodeId: null,
  openNodeIdSet: {},
  byPath: {},
  byId: {},
  flatPages: [],
  reloadTree: async () => {
    set({ loading: true, error: null })
    try {
      const tree = await getTree()
      const indexes = buildIndexes(tree)
      set({
        tree,
        byPath: indexes.byPath,
        byId: indexes.byId,
        flatPages: indexes.flatPages,
      })
    } catch (error) {
      set({ error: (error as Error).message })
    } finally {
      set({ loading: false })
    }
  },
  toggleNode: (id) => {
    const openNodeIdSet = { ...get().openNodeIdSet }
    if (openNodeIdSet[id]) {
      delete openNodeIdSet[id]
    } else {
      openNodeIdSet[id] = true
    }
    set({ openNodeIdSet })
  },
  openNode: (id) => {
    set((state) => ({ openNodeIdSet: { ...state.openNodeIdSet, [id]: true } }))
  },
  closeNode: (id) => {
    const openNodeIdSet = { ...get().openNodeIdSet }
    delete openNodeIdSet[id]
    set({ openNodeIdSet })
  },
  setActiveNodeId: (id) => set({ activeNodeId: id }),
  getPageByPath: (path) => get().byPath[path] ?? null,
  getPageById: (id) => get().byId[id] ?? null,
  openAncestorsForPath: (path) => {
    const node = get().byPath[path]
    if (!node) {
      return
    }
    const openNodeIdSet = { ...get().openNodeIdSet }
    let currentPath = node.parentPath
    while (currentPath !== null) {
      const currentNode = get().byPath[currentPath]
      if (!currentNode) {
        break
      }
      openNodeIdSet[currentNode.id] = true
      currentPath = currentNode.parentPath
    }
    set({ openNodeIdSet })
  },
}))
