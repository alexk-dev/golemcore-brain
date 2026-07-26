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
import { ChevronDown, ChevronRight, Copy, FileText, Folder, FolderOpen, FolderPlus, List, MoreVertical, Move, Pencil, Plus, Repeat2, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import type { MouseEvent } from 'react'

import type { WikiNodeKind, WikiTreeNode } from '../types'

interface TreeNodeItemProps {
  node: WikiTreeNode
  activePath: string
  openPaths: string[]
  canCreate: boolean
  canEdit: boolean
  depth?: number
  onNavigate: (path: string) => void
  onToggle: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
  onEdit: (path: string) => void
  onMove: (path: string) => void
  onCopy: (path: string) => void
  onDelete: (path: string) => void
  onSort: (node: WikiTreeNode) => void
  onConvert: (path: string, targetKind: Exclude<WikiNodeKind, 'ROOT'>) => void
}

export function TreeNodeItem({
  node,
  activePath,
  openPaths,
  canCreate,
  canEdit,
  depth = 0,
  onNavigate,
  onToggle,
  onCreate,
  onEdit,
  onMove,
  onCopy,
  onDelete,
  onSort,
  onConvert,
}: TreeNodeItemProps) {
  const isActive = node.path === activePath
  const isOpen = node.kind !== 'PAGE' && openPaths.includes(node.path)
  const hasChildren = node.children.length > 0
  const convertTargetKind: Exclude<WikiNodeKind, 'ROOT'> | null =
    node.kind === 'PAGE'
      ? 'SECTION'
      : node.kind === 'SECTION' && !hasChildren
        ? 'PAGE'
        : null
  const canCreateHere = canCreate && node.kind !== 'PAGE'
  const hasActions = canEdit || canCreateHere

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
          'tree-node__row group',
          isActive
            ? 'bg-accent/14 text-foreground shadow-sm'
            : 'text-sidebar-foreground/88 hover:bg-white/8 hover:text-sidebar-foreground',
        )}
        style={{ paddingLeft: `${depth * 0.75 + 0.5}rem` }}
      >
        {node.kind === 'PAGE' ? (
          <span className="tree-node__leading" aria-hidden="true">
            <FileText size={15} />
          </span>
        ) : (
          <button
            type="button"
            className="tree-node__leading tree-node__leading--interactive"
            onClick={() => onToggle(node.path)}
            aria-label={isOpen ? 'Collapse section' : 'Expand section'}
            aria-expanded={isOpen}
          >
            {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
          </button>
        )}
        <button
          type="button"
          className="tree-node__label"
          onClick={handleNavigate}
          title={node.title}
          aria-label={node.title}
          aria-current={isActive ? 'page' : undefined}
        >
          {node.kind === 'PAGE' ? null : isOpen ? (
            <FolderOpen size={16} className="shrink-0" />
          ) : (
            <Folder size={16} className="shrink-0" />
          )}
          <span className="truncate">{node.title}</span>
        </button>
        {hasActions ? (
          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <button
                type="button"
                className="tree-node__actions-trigger"
                onClick={(event) => event.stopPropagation()}
                title={`Actions for ${node.title}`}
                aria-label={`Actions for ${node.title}`}
              >
                <MoreVertical size={16} />
              </button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <DropdownMenu.Content align="end" sideOffset={4} collisionPadding={8} className="tree-node__menu">
                <DropdownMenu.Label className="tree-node__menu-label">{node.title}</DropdownMenu.Label>
                <DropdownMenu.Separator className="tree-node__menu-separator" />
                {canCreateHere ? (
                  <>
                    <DropdownMenu.Item
                      className="tree-node__menu-item"
                      aria-label={`New page under ${node.title}`}
                      onSelect={() => onCreate(node.path, 'PAGE')}
                    >
                      <Plus size={15} aria-hidden="true" />
                      <span>New page</span>
                    </DropdownMenu.Item>
                    <DropdownMenu.Item
                      className="tree-node__menu-item"
                      aria-label={`New section under ${node.title}`}
                      onSelect={() => onCreate(node.path, 'SECTION')}
                    >
                      <FolderPlus size={15} aria-hidden="true" />
                      <span>New section</span>
                    </DropdownMenu.Item>
                  </>
                ) : null}
                {canEdit ? (
                  <>
                    {canCreateHere ? <DropdownMenu.Separator className="tree-node__menu-separator" /> : null}
                    <DropdownMenu.Item
                      className="tree-node__menu-item"
                      aria-label={`Edit ${node.title}`}
                      onSelect={() => onEdit(node.path)}
                    >
                      <Pencil size={15} aria-hidden="true" />
                      <span>Edit</span>
                    </DropdownMenu.Item>
                    <DropdownMenu.Item
                      className="tree-node__menu-item"
                      aria-label={`Move ${node.title}`}
                      onSelect={() => onMove(node.path)}
                    >
                      <Move size={15} aria-hidden="true" />
                      <span>Move</span>
                    </DropdownMenu.Item>
                    <DropdownMenu.Item
                      className="tree-node__menu-item"
                      aria-label={`Copy ${node.title}`}
                      onSelect={() => onCopy(node.path)}
                    >
                      <Copy size={15} aria-hidden="true" />
                      <span>Copy</span>
                    </DropdownMenu.Item>
                    {node.kind !== 'PAGE' ? (
                      <DropdownMenu.Item
                        className="tree-node__menu-item"
                        aria-label={`Sort ${node.title}`}
                        onSelect={() => onSort(node)}
                      >
                        <List size={15} aria-hidden="true" />
                        <span>Sort children</span>
                      </DropdownMenu.Item>
                    ) : null}
                    {convertTargetKind ? (
                      <DropdownMenu.Item
                        className="tree-node__menu-item"
                        aria-label={`Convert ${node.title} to ${convertTargetKind.toLowerCase()}`}
                        onSelect={() => onConvert(node.path, convertTargetKind)}
                      >
                        <Repeat2 size={15} aria-hidden="true" />
                        <span>Convert to {convertTargetKind.toLowerCase()}</span>
                      </DropdownMenu.Item>
                    ) : null}
                    <DropdownMenu.Separator className="tree-node__menu-separator" />
                    <DropdownMenu.Item
                      className="tree-node__menu-item tree-node__menu-item--danger"
                      aria-label={`Delete ${node.title}`}
                      onSelect={() => onDelete(node.path)}
                    >
                      <Trash2 size={15} aria-hidden="true" />
                      <span>Delete</span>
                    </DropdownMenu.Item>
                  </>
                ) : null}
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
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
              canEdit={canEdit}
              depth={depth + 1}
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
        </ul>
      ) : null}
    </li>
  )
}
