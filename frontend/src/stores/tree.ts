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
  expandAll: () => void
  collapseAll: () => void
  setActiveNodeId: (id: string | null) => void
  getPageByPath: (path: string) => WikiTreeNode | null
  getPageById: (id: string) => WikiTreeNode | null
  openAncestorsForPath: (path: string) => void
  manualNodeStateById: Record<string, boolean>
  mustOpenNodeIdSet: Record<string, true>
  suggestedOpenNodeIdSet: Record<string, true>
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

function computeOpenNodeIdSet(
  manualNodeStateById: Record<string, boolean>,
  mustOpenNodeIdSet: Record<string, true>,
  suggestedOpenNodeIdSet: Record<string, true>,
): Record<string, true> {
  const openNodeIdSet: Record<string, true> = {}
  const keys = new Set([
    ...Object.keys(manualNodeStateById),
    ...Object.keys(mustOpenNodeIdSet),
    ...Object.keys(suggestedOpenNodeIdSet),
  ])

  for (const key of keys) {
    if (mustOpenNodeIdSet[key]) {
      openNodeIdSet[key] = true
      continue
    }
    if (manualNodeStateById[key] === false) {
      continue
    }
    if (manualNodeStateById[key] === true || suggestedOpenNodeIdSet[key]) {
      openNodeIdSet[key] = true
    }
  }

  return openNodeIdSet
}

function buildRouteOpenState(byPath: Record<string, WikiTreeNode>, path: string) {
  const node = byPath[path]
  const mustOpenNodeIdSet: Record<string, true> = {}
  const suggestedOpenNodeIdSet: Record<string, true> = {}
  if (!node) {
    return { mustOpenNodeIdSet, suggestedOpenNodeIdSet }
  }

  if (node.kind !== 'PAGE') {
    suggestedOpenNodeIdSet[node.id] = true
  }

  let currentPath = node.parentPath
  while (currentPath !== null) {
    const currentNode = byPath[currentPath]
    if (!currentNode) {
      break
    }
    mustOpenNodeIdSet[currentNode.id] = true
    currentPath = currentNode.parentPath
  }

  return { mustOpenNodeIdSet, suggestedOpenNodeIdSet }
}

function collectContainerNodeIds(node: WikiTreeNode | null) {
  const ids: string[] = []
  const walk = (currentNode: WikiTreeNode) => {
    if (currentNode.kind === 'PAGE') {
      return
    }
    ids.push(currentNode.id)
    currentNode.children.forEach(walk)
  }
  if (node) {
    walk(node)
  }
  return ids
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
  manualNodeStateById: {},
  mustOpenNodeIdSet: {},
  suggestedOpenNodeIdSet: {},
  reloadTree: async () => {
    set({ loading: true, error: null })
    try {
      const tree = await getTree()
      const indexes = buildIndexes(tree)
      const currentPath = get().activeNodeId ? indexes.byId[get().activeNodeId]?.path ?? '' : ''
      const routeState = buildRouteOpenState(indexes.byPath, currentPath)
      set({
        tree,
        byPath: indexes.byPath,
        byId: indexes.byId,
        flatPages: indexes.flatPages,
        mustOpenNodeIdSet: routeState.mustOpenNodeIdSet,
        suggestedOpenNodeIdSet: routeState.suggestedOpenNodeIdSet,
        openNodeIdSet: computeOpenNodeIdSet(get().manualNodeStateById, routeState.mustOpenNodeIdSet, routeState.suggestedOpenNodeIdSet),
      })
    } catch (error) {
      set({ error: (error as Error).message })
    } finally {
      set({ loading: false })
    }
  },
  toggleNode: (id) => {
    const state = get()
    const manualNodeStateById = { ...state.manualNodeStateById }
    const nextValue = !state.openNodeIdSet[id]
    manualNodeStateById[id] = nextValue
    set({
      manualNodeStateById,
      openNodeIdSet: computeOpenNodeIdSet(manualNodeStateById, state.mustOpenNodeIdSet, state.suggestedOpenNodeIdSet),
    })
  },
  openNode: (id) => {
    const state = get()
    const manualNodeStateById = { ...state.manualNodeStateById, [id]: true }
    set({
      manualNodeStateById,
      openNodeIdSet: computeOpenNodeIdSet(manualNodeStateById, state.mustOpenNodeIdSet, state.suggestedOpenNodeIdSet),
    })
  },
  closeNode: (id) => {
    const state = get()
    const manualNodeStateById = { ...state.manualNodeStateById, [id]: false }
    set({
      manualNodeStateById,
      openNodeIdSet: computeOpenNodeIdSet(manualNodeStateById, state.mustOpenNodeIdSet, state.suggestedOpenNodeIdSet),
    })
  },
  expandAll: () => {
    const state = get()
    const manualNodeStateById = { ...state.manualNodeStateById }
    collectContainerNodeIds(state.tree).forEach((id) => {
      manualNodeStateById[id] = true
    })
    set({
      manualNodeStateById,
      openNodeIdSet: computeOpenNodeIdSet(manualNodeStateById, state.mustOpenNodeIdSet, state.suggestedOpenNodeIdSet),
    })
  },
  collapseAll: () => {
    const state = get()
    const manualNodeStateById = { ...state.manualNodeStateById }
    collectContainerNodeIds(state.tree).forEach((id) => {
      manualNodeStateById[id] = false
    })
    set({
      manualNodeStateById,
      openNodeIdSet: computeOpenNodeIdSet(manualNodeStateById, state.mustOpenNodeIdSet, state.suggestedOpenNodeIdSet),
    })
  },
  setActiveNodeId: (id) => set({ activeNodeId: id }),
  getPageByPath: (path) => get().byPath[path] ?? null,
  getPageById: (id) => get().byId[id] ?? null,
  openAncestorsForPath: (path) => {
    const state = get()
    const routeState = buildRouteOpenState(state.byPath, path)
    set({
      mustOpenNodeIdSet: routeState.mustOpenNodeIdSet,
      suggestedOpenNodeIdSet: routeState.suggestedOpenNodeIdSet,
      openNodeIdSet: computeOpenNodeIdSet(state.manualNodeStateById, routeState.mustOpenNodeIdSet, routeState.suggestedOpenNodeIdSet),
    })
  },
}))
