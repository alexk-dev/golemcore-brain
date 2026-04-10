import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { create } from 'zustand'

export interface ToolbarAction {
  id: string
  label: string
  title?: string
  hotkey?: string
  hotkeyLabel?: string
  icon?: ReactNode
  variant?: 'primary' | 'secondary' | 'danger'
  hidden?: boolean
  disabled?: boolean
  onRun: () => void
}

interface ToolbarState {
  actions: ToolbarAction[]
  setActions: (actions: ToolbarAction[]) => void
}

export const useToolbarStore = create<ToolbarState>((set) => ({
  actions: [],
  setActions: (actions) => set({ actions }),
}))

export function useToolbarActions(actions: ToolbarAction[]) {
  const setActions = useToolbarStore((state) => state.setActions)

  useEffect(() => {
    setActions(actions)
    return () => setActions([])
  }, [actions, setActions])
}

function modifierMatches(event: KeyboardEvent, token: string) {
  if (token === 'mod') {
    return event.metaKey || event.ctrlKey
  }
  if (token === 'shift') {
    return event.shiftKey
  }
  if (token === 'alt' || token === 'option') {
    return event.altKey
  }
  return true
}

export function matchesToolbarHotkey(event: KeyboardEvent, hotkey: string) {
  const tokens = hotkey.toLowerCase().split('+').map((token) => token.trim()).filter(Boolean)
  const keyToken = tokens.find((token) => !['mod', 'shift', 'alt', 'option'].includes(token))
  if (!keyToken) {
    return false
  }

  if (tokens.includes('mod') !== (event.metaKey || event.ctrlKey)) {
    return false
  }
  if (tokens.includes('shift') !== event.shiftKey) {
    return false
  }
  if ((tokens.includes('alt') || tokens.includes('option')) !== event.altKey) {
    return false
  }
  if (!tokens.every((token) => modifierMatches(event, token))) {
    return false
  }

  const normalizedKey = event.key.toLowerCase()
  const normalizedCode = event.code.toLowerCase()
  return keyToken === normalizedKey || keyToken === normalizedCode || `key${keyToken}` === normalizedCode
}
