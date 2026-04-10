import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Plus } from 'lucide-react'
import clsx from 'clsx'
import type { MouseEvent } from 'react'

import type { WikiTreeNode } from '../types'

interface TreeNodeItemProps {
  node: WikiTreeNode
  activePath: string
  openPaths: string[]
  canCreate: boolean
  depth?: number
  onNavigate: (path: string) => void
  onToggle: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
}

export function TreeNodeItem({
  node,
  activePath,
  openPaths,
  canCreate,
  depth = 0,
  onNavigate,
  onToggle,
  onCreate,
}: TreeNodeItemProps) {
  const isActive = node.path === activePath
  const isOpen = node.kind !== 'PAGE' && openPaths.includes(node.path)
  const hasChildren = node.children.length > 0

  const handleNavigate = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation()
    if (node.kind !== 'PAGE' && isActive) {
      onToggle(node.path)
      return
    }
    onNavigate(node.path)
  }

  return (
    <li className="space-y-1">
      <div
        className={clsx(
          'group flex items-center gap-2 rounded-xl px-2 py-2 text-left text-sm transition',
          isActive
            ? 'bg-accent/14 text-foreground shadow-sm'
            : 'text-sidebar-foreground/88 hover:bg-white/8 hover:text-sidebar-foreground',
        )}
        style={{ paddingLeft: `${depth * 0.75 + 0.5}rem` }}
      >
        {node.kind === 'PAGE' ? (
          <span className="flex h-6 w-6 items-center justify-center text-sidebar-foreground/60">
            <FileText size={15} />
          </span>
        ) : (
          <button
            type="button"
            className="flex h-6 w-6 items-center justify-center rounded-lg text-sidebar-foreground/70 transition hover:bg-white/10"
            onClick={() => onToggle(node.path)}
            aria-label={isOpen ? 'Collapse section' : 'Expand section'}
          >
            {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
          </button>
        )}
        <button
          type="button"
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
          onClick={handleNavigate}
        >
          {node.kind === 'PAGE' ? null : isOpen ? (
            <FolderOpen size={16} className="shrink-0" />
          ) : (
            <Folder size={16} className="shrink-0" />
          )}
          <span className="truncate">{node.title}</span>
        </button>
        {canCreate && node.kind !== 'PAGE' ? (
          <div className="hidden items-center gap-1 group-hover:flex">
            <button
              type="button"
              className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
              onClick={() => onCreate(node.path, 'PAGE')}
              title="New page"
            >
              <Plus size={14} />
            </button>
            <button
              type="button"
              className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
              onClick={() => onCreate(node.path, 'SECTION')}
              title="New section"
            >
              <Folder size={14} />
            </button>
          </div>
        ) : null}
      </div>
      {node.kind !== 'PAGE' && hasChildren && isOpen ? (
        <ul className="space-y-1">
          {node.children.map((childNode) => (
            <TreeNodeItem
              key={childNode.path || childNode.slug}
              node={childNode}
              activePath={activePath}
              openPaths={openPaths}
              canCreate={canCreate}
              depth={depth + 1}
              onNavigate={onNavigate}
              onToggle={onToggle}
              onCreate={onCreate}
            />
          ))}
        </ul>
      ) : null}
    </li>
  )
}
