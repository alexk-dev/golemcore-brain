import { useEffect } from 'react'

import { matchesToolbarHotkey, useToolbarStore } from './toolbarStore'

function buttonClassName(variant: 'primary' | 'secondary' | 'danger' = 'secondary') {
  if (variant === 'primary') {
    return 'action-button-primary'
  }
  if (variant === 'danger') {
    return 'action-button-danger'
  }
  return 'action-button-secondary'
}

export function Toolbar() {
  const actions = useToolbarStore((state) => state.actions)

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

  return (
    <>
      {actions.filter((action) => !action.hidden).map((action) => {
        const title = action.title ?? action.label
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
            <span className="hidden md:inline">{action.label}</span>
          </button>
        )
      })}
    </>
  )
}
