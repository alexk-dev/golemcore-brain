import type { ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTreeStore } from '../../stores/tree'
import { useSpaceStore } from '../../stores/space'

interface SpaceSwitcherProps {
  className?: string
}

export function SpaceSwitcher({ className }: SpaceSwitcherProps) {
  const spaces = useSpaceStore((state) => state.spaces)
  const activeSlug = useSpaceStore((state) => state.activeSlug)
  const setActiveSlug = useSpaceStore((state) => state.setActiveSlug)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const navigate = useNavigate()

  if (spaces.length === 0) {
    return null
  }

  const handleChange = async (event: ChangeEvent<HTMLSelectElement>) => {
    const nextSlug = event.target.value
    setActiveSlug(nextSlug)
    navigate('/')
    await reloadTree()
  }

  return (
    <select
      className={className ?? 'action-button-secondary'}
      value={activeSlug}
      onChange={handleChange}
      aria-label="Active space"
      title="Active space"
    >
      {spaces.map((space) => (
        <option key={space.id} value={space.slug}>
          {space.name}
        </option>
      ))}
    </select>
  )
}
