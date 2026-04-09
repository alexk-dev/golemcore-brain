import { FolderTree, Search as SearchIcon } from 'lucide-react'

import type { WikiTreeNode } from '../../types'
import { TreeNodeItem } from '../../components/TreeNodeItem'

interface SidebarProps {
  tree: WikiTreeNode | null
  activePath: string
  openPaths: string[]
  onNavigate: (path: string) => void
  onToggle: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
  onOpenSearch: () => void
}

export function Sidebar({
  tree,
  activePath,
  openPaths,
  onNavigate,
  onToggle,
  onCreate,
  onOpenSearch,
}: SidebarProps) {
  return (
    <aside className="sidebar" data-testid="sidebar" id="sidebar">
      <div className="sidebar__inner">
        <div className="sidebar__tabs">
          <div className="sidebar__tabs-list">
            <button className="sidebar__tab-button sidebar__tab-button--active">
              <FolderTree size={16} /> Tree
            </button>
            <button className="sidebar__tab-button sidebar__tab-button--inactive" onClick={onOpenSearch}>
              <SearchIcon size={16} /> Search
            </button>
          </div>
        </div>
        <div className="sidebar__content custom-scrollbar overflow-y-auto px-2 py-3">
          <div className="tree-view">
            <div className="tree-view__toolbar">
              <button
                type="button"
                className="action-button-secondary"
                onClick={() => onCreate('', 'PAGE')}
              >
                New page
              </button>
              <button
                type="button"
                className="action-button-secondary"
                onClick={() => onCreate('', 'SECTION')}
              >
                New section
              </button>
            </div>
            <div className="tree-view__nodes">
              {tree?.children?.map((node) => (
                <TreeNodeItem
                  key={node.id}
                  node={node}
                  activePath={activePath}
                  openPaths={openPaths}
                  onNavigate={onNavigate}
                  onToggle={onToggle}
                  onCreate={onCreate}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </aside>
  )
}
