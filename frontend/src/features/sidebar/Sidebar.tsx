import { FolderTree, Search as SearchIcon } from 'lucide-react'
import { useState } from 'react'

import { SearchDialog } from '../../components/SearchDialog'
import type { WikiTreeNode } from '../../types'
import { TreeNodeItem } from '../../components/TreeNodeItem'

interface SidebarProps {
  tree: WikiTreeNode | null
  activePath: string
  openPaths: string[]
  canCreate: boolean
  onNavigate: (path: string) => void
  onToggle: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
  onOpenSearch: () => void
}

export function Sidebar({
  tree,
  activePath,
  openPaths,
  canCreate,
  onNavigate,
  onToggle,
  onCreate,
  onOpenSearch,
}: SidebarProps) {
  const [activeTab, setActiveTab] = useState<'tree' | 'search'>('tree')

  return (
    <aside className="sidebar" data-testid="sidebar" id="sidebar">
      <div className="sidebar__inner">
        <div className="sidebar__tabs">
          <div className="sidebar__tabs-list">
            <button
              className={`sidebar__tab-button ${activeTab === 'tree' ? 'sidebar__tab-button--active' : 'sidebar__tab-button--inactive'}`}
              onClick={() => setActiveTab('tree')}
            >
              <FolderTree size={16} /> Tree
            </button>
            <button
              className={`sidebar__tab-button ${activeTab === 'search' ? 'sidebar__tab-button--active' : 'sidebar__tab-button--inactive'}`}
              onClick={() => {
                setActiveTab('search')
                onOpenSearch()
              }}
            >
              <SearchIcon size={16} /> Search
            </button>
          </div>
        </div>
        <div className="sidebar__content custom-scrollbar overflow-y-auto px-2 py-3">
          {activeTab === 'tree' ? (
            <div className="tree-view">
              {canCreate ? (
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
              ) : null}
              <div className="tree-view__nodes">
                {tree?.children?.map((node) => (
                  <TreeNodeItem
                    key={node.id}
                    node={node}
                    activePath={activePath}
                    openPaths={openPaths}
                    canCreate={canCreate}
                    onNavigate={onNavigate}
                    onToggle={onToggle}
                    onCreate={onCreate}
                  />
                ))}
              </div>
            </div>
          ) : (
            <SearchDialog
              open={true}
              tree={tree}
              embedded={true}
              onOpenChange={() => setActiveTab('tree')}
              onNavigate={onNavigate}
            />
          )}
        </div>
      </div>
    </aside>
  )
}
