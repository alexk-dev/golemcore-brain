import { FileSearch } from 'lucide-react'

interface PageQuickSwitcherTriggerProps {
  onOpen: () => void
}

export function PageQuickSwitcherTrigger({ onOpen }: PageQuickSwitcherTriggerProps) {
  return (
    <button type="button" className="action-button-secondary" onClick={onOpen}>
      <FileSearch size={16} />
      Go to page
    </button>
  )
}
