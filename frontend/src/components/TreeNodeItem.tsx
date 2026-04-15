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

import { ChevronDown, ChevronRight, Copy, FileText, Folder, FolderOpen, List, MoreVertical, Move, Pencil, Plus, Repeat2, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import { useState } from 'react'
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
  const [mobileActionsOpen, setMobileActionsOpen] = useState(false)
  const isActive = node.path === activePath
  const isOpen = node.kind !== 'PAGE' && openPaths.includes(node.path)
  const hasChildren = node.children.length > 0
  const convertTargetKind: Exclude<WikiNodeKind, 'ROOT'> | null =
    node.kind === 'PAGE'
      ? 'SECTION'
      : node.kind === 'SECTION' && !hasChildren
        ? 'PAGE'
        : null

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
          title={node.title}
          aria-label={node.title}
        >
          {node.kind === 'PAGE' ? null : isOpen ? (
            <FolderOpen size={16} className="shrink-0" />
          ) : (
            <Folder size={16} className="shrink-0" />
          )}
          <span className="truncate">{node.title}</span>
        </button>
        {canEdit || (canCreate && node.kind !== 'PAGE') ? (
          <>
            <div className="hidden items-center gap-1 group-hover:flex group-focus-within:flex max-md:hidden">
              {canEdit ? (
                <>
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      onEdit(node.path)
                    }}
                    title={`Edit ${node.title}`}
                    aria-label={`Edit ${node.title}`}
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      onMove(node.path)
                    }}
                    title={`Move ${node.title}`}
                    aria-label={`Move ${node.title}`}
                  >
                    <Move size={14} />
                  </button>
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      onCopy(node.path)
                    }}
                    title={`Copy ${node.title}`}
                    aria-label={`Copy ${node.title}`}
                  >
                    <Copy size={14} />
                  </button>
                  {node.kind !== 'PAGE' ? (
                    <button
                      type="button"
                      className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                      onClick={(event) => {
                        event.stopPropagation()
                        onSort(node)
                      }}
                      title={`Sort ${node.title}`}
                      aria-label={`Sort ${node.title}`}
                    >
                      <List size={14} />
                    </button>
                  ) : null}
                  {convertTargetKind ? (
                    <button
                      type="button"
                      className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                      onClick={(event) => {
                        event.stopPropagation()
                        onConvert(node.path, convertTargetKind)
                      }}
                      title={`Convert ${node.title} to ${convertTargetKind.toLowerCase()}`}
                      aria-label={`Convert ${node.title} to ${convertTargetKind.toLowerCase()}`}
                    >
                      <Repeat2 size={14} />
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-danger"
                    onClick={(event) => {
                      event.stopPropagation()
                      onDelete(node.path)
                    }}
                    title={`Delete ${node.title}`}
                    aria-label={`Delete ${node.title}`}
                  >
                    <Trash2 size={14} />
                  </button>
                </>
              ) : null}
              {canCreate && node.kind !== 'PAGE' ? (
                <>
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      onCreate(node.path, 'PAGE')
                    }}
                    title="New page"
                    aria-label={`New page under ${node.title}`}
                  >
                    <Plus size={14} />
                  </button>
                  <button
                    type="button"
                    className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      onCreate(node.path, 'SECTION')
                    }}
                    title="New section"
                    aria-label={`New section under ${node.title}`}
                  >
                    <Folder size={14} />
                  </button>
                </>
              ) : null}
            </div>
            <div className="relative hidden max-md:block">
              <button
                type="button"
                className="rounded-lg p-1.5 text-sidebar-foreground/60 transition hover:bg-white/10 hover:text-sidebar-foreground"
                onClick={(event) => {
                  event.stopPropagation()
                  setMobileActionsOpen((open) => !open)
                }}
                title={`More actions for ${node.title}`}
                aria-label={`More actions for ${node.title}`}
              >
                <MoreVertical size={14} />
              </button>
              {mobileActionsOpen ? (
                <div className="absolute right-0 top-full z-20 mt-1 flex min-w-40 flex-col gap-1 rounded-lg border border-surface-border bg-surface px-2 py-2 shadow-lg">
                  {canEdit ? (
                    <>
                      <button
                        type="button"
                        className="action-button-secondary justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onEdit(node.path)
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="action-button-secondary justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onMove(node.path)
                        }}
                      >
                        Move
                      </button>
                      <button
                        type="button"
                        className="action-button-secondary justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onCopy(node.path)
                        }}
                      >
                        Copy
                      </button>
                      {node.kind !== 'PAGE' ? (
                        <button
                          type="button"
                          className="action-button-secondary justify-start"
                          onClick={(event) => {
                            event.stopPropagation()
                            setMobileActionsOpen(false)
                            onSort(node)
                          }}
                        >
                          Sort
                        </button>
                      ) : null}
                      {convertTargetKind ? (
                        <button
                          type="button"
                          className="action-button-secondary justify-start"
                          onClick={(event) => {
                            event.stopPropagation()
                            setMobileActionsOpen(false)
                            onConvert(node.path, convertTargetKind)
                          }}
                        >
                          Convert to {convertTargetKind.toLowerCase()}
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className="action-button-danger justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onDelete(node.path)
                        }}
                      >
                        Delete
                      </button>
                    </>
                  ) : null}
                  {canCreate && node.kind !== 'PAGE' ? (
                    <>
                      <button
                        type="button"
                        className="action-button-secondary justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onCreate(node.path, 'PAGE')
                        }}
                      >
                        New page
                      </button>
                      <button
                        type="button"
                        className="action-button-secondary justify-start"
                        onClick={(event) => {
                          event.stopPropagation()
                          setMobileActionsOpen(false)
                          onCreate(node.path, 'SECTION')
                        }}
                      >
                        New section
                      </button>
                    </>
                  ) : null}
                </div>
              ) : null}
            </div>
          </>
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
