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

import { ChevronsDown, ChevronsUp, FolderTree, List, Search as SearchIcon } from 'lucide-react'
import { useState } from 'react'

import { SearchDialog } from '../../components/SearchDialog'
import type { WikiNodeKind, WikiTreeNode } from '../../types'
import { TreeNodeItem } from '../../components/TreeNodeItem'

interface SidebarProps {
  tree: WikiTreeNode | null
  activePath: string
  openPaths: string[]
  canCreate: boolean
  canEdit: boolean
  onNavigate: (path: string) => void
  onToggle: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
  onEdit: (path: string) => void
  onMove: (path: string) => void
  onCopy: (path: string) => void
  onDelete: (path: string) => void
  onSort: (node: WikiTreeNode) => void
  onConvert: (path: string, targetKind: Exclude<WikiNodeKind, 'ROOT'>) => void
  onExpandAll: () => void
  onCollapseAll: () => void
  onOpenSearch: () => void
  imageVersion?: string | null
}

export function Sidebar({
  tree,
  activePath,
  openPaths,
  canCreate,
  canEdit,
  onNavigate,
  onToggle,
  onCreate,
  onEdit,
  onMove,
  onCopy,
  onDelete,
  onSort,
  onConvert,
  onExpandAll,
  onCollapseAll,
  onOpenSearch,
  imageVersion,
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
              <div className="tree-view__toolbar">
                {canCreate ? (
                  <>
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
                  </>
                ) : null}
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={onExpandAll}
                  title="Expand all"
                  aria-label="Expand all"
                >
                  <ChevronsDown size={16} />
                </button>
                <button
                  type="button"
                  className="action-button-secondary"
                  onClick={onCollapseAll}
                  title="Collapse all"
                  aria-label="Collapse all"
                >
                  <ChevronsUp size={16} />
                </button>
                {canEdit && tree ? (
                  <button
                    type="button"
                    className="action-button-secondary"
                    onClick={() => onSort(tree)}
                    title="Sort root pages"
                    aria-label="Sort root pages"
                  >
                    <List size={16} />
                  </button>
                ) : null}
              </div>
              <div className="tree-view__nodes">
                {tree?.children?.map((node) => (
                  <TreeNodeItem
                    key={node.id}
                    node={node}
                    activePath={activePath}
                    openPaths={openPaths}
                    canCreate={canCreate}
                    canEdit={canEdit}
                    onNavigate={onNavigate}
                    onToggle={onToggle}
                    onCreate={onCreate}
                    onEdit={onEdit}
                    onMove={onMove}
                    onCopy={onCopy}
                    onDelete={onDelete}
                    onSort={onSort}
                    onConvert={onConvert}
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
        {imageVersion ? (
          <div className="sidebar__footer">
            <span className="sidebar__image-version" title={`Image version ${imageVersion}`}>
              {imageVersion}
            </span>
          </div>
        ) : null}
      </div>
    </aside>
  )
}
