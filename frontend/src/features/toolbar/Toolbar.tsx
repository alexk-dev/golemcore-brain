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

import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { MoreHorizontal } from 'lucide-react'
import { useEffect, useState } from 'react'

import { matchesToolbarHotkey, useToolbarStore } from './toolbarStore'
import type { ToolbarAction } from './toolbarStore'

function buttonClassName(variant: 'primary' | 'secondary' | 'danger' = 'secondary') {
  if (variant === 'primary') {
    return 'action-button-primary'
  }
  if (variant === 'danger') {
    return 'action-button-danger'
  }
  return 'action-button-secondary'
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => (
    typeof window === 'undefined' ? false : window.matchMedia(query).matches
  ))

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined
    }
    const mediaQuery = window.matchMedia(query)
    const handleChange = () => setMatches(mediaQuery.matches)
    handleChange()
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [query])

  return matches
}

function actionTitle(action: ToolbarAction) {
  return action.title ?? action.label
}

export function Toolbar() {
  const actions = useToolbarStore((state) => state.actions)
  const isDesktop = useMediaQuery('(min-width: 768px)')

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      for (const action of actions) {
        if (!action.hotkey || action.disabled || !matchesToolbarHotkey(event, action.hotkey)) {
          continue
        }
        event.preventDefault()
        action.onRun()
        return
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [actions])

  const visibleActions = actions.filter((action) => !action.hidden)
  const mobilePrimaryActions = visibleActions.filter((action) => action.variant === 'primary' || action.id === 'search')
  const mobileOverflowActions = visibleActions.filter((action) => !mobilePrimaryActions.some((item) => item.id === action.id))
  const renderedActions = isDesktop ? visibleActions : mobilePrimaryActions

  return (
    <>
      {renderedActions.map((action) => {
        const title = actionTitle(action)
        return (
          <button
            key={action.id}
            type="button"
            className={buttonClassName(action.variant)}
            onClick={action.onRun}
            disabled={action.disabled}
            title={action.hotkeyLabel ? `${title} (${action.hotkeyLabel})` : title}
            aria-label={title}
          >
            {action.icon}
            <span className={isDesktop ? '' : 'sr-only'}>{action.label}</span>
          </button>
        )
      })}
      {!isDesktop && mobileOverflowActions.length > 0 ? (
        <DropdownMenu.Root modal={false}>
          <DropdownMenu.Trigger asChild>
            <button type="button" className="action-button-secondary" aria-label="More actions" title="More actions">
              <MoreHorizontal size={16} aria-hidden="true" />
            </button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content align="end" sideOffset={6} className="toolbar-overflow__content">
              {mobileOverflowActions.map((action) => {
                const title = actionTitle(action)
                return (
                  <DropdownMenu.Item
                    key={action.id}
                    className="toolbar-overflow__item"
                    disabled={action.disabled}
                    onSelect={(event) => {
                      event.preventDefault()
                      action.onRun()
                    }}
                  >
                    {action.icon}
                    <span>{action.label}</span>
                    {action.hotkeyLabel ? <span className="toolbar-overflow__shortcut">{action.hotkeyLabel}</span> : null}
                    <span className="sr-only">{title}</span>
                  </DropdownMenu.Item>
                )
              })}
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
      ) : null}
    </>
  )
}
