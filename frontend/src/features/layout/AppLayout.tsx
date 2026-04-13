import { Menu } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { Sidebar } from '../sidebar/Sidebar'
import { SpaceSwitcher } from '../spaces/SpaceSwitcher'
import { Toolbar } from '../toolbar/Toolbar'
import type { WikiNodeKind, WikiTreeNode } from '../../types'
import { UserMenu } from './UserMenu'

interface AppLayoutProps {
  children: ReactNode
  siteTitle: string
  tree: WikiTreeNode | null
  activePath: string
  openPaths: string[]
  sidebarVisible: boolean
  onToggleSidebar: () => void
  hideHeader?: boolean
  onNavigate: (path: string) => void
  onToggleNode: (path: string) => void
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
  currentUsername?: string | null
  canManageUsers: boolean
  canAccessAccount: boolean
  canCreate: boolean
  canEditCurrent: boolean
  editorTitle?: string | null
  editorPath?: string | null
  editorDirty?: boolean
  onLogout: () => void
}

export function AppLayout({
  children,
  siteTitle,
  tree,
  activePath,
  openPaths,
  sidebarVisible,
  onToggleSidebar,
  hideHeader = false,
  onNavigate,
  onToggleNode,
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
  currentUsername,
  canManageUsers,
  canAccessAccount,
  canCreate,
  canEditCurrent,
  editorTitle,
  editorPath,
  editorDirty = false,
  onLogout,
}: AppLayoutProps) {
  const location = useLocation()
  const isChatRoute = location.pathname === '/chat'

  if (hideHeader) {
    return (
      <div className="min-h-screen bg-background text-foreground">
        <main className="app-layout__chromeless-main custom-scrollbar">{children}</main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="app-layout__header">
        <div className="app-layout__header-inner">
          <div className="app-layout__sidebar-toggle-container">
            <button
              type="button"
              className="app-layout__sidebar-toggle-button"
              onClick={onToggleSidebar}
              aria-label="Toggle Sidebar"
            >
              <Menu size={18} />
            </button>
          </div>
          <div className="app-layout__logo-n-title">
            <h2>{siteTitle}</h2>
          </div>
          <div className="app-layout__editor-title-bar-container">
            {editorTitle ? (
              <div className="editor-shell-title" title={editorTitle}>
                <span className="editor-shell-title__title">{editorTitle}</span>
                {editorDirty ? <span className="editor-title-bar__dirty-indicator">Unsaved changes</span> : null}
                {editorPath ? <span className="editor-shell-title__path">/{editorPath}</span> : null}
              </div>
            ) : null}
          </div>
          <div className="app-layout__editor-toolbar-container">
            <Toolbar />
            <Link to="/chat" className={isChatRoute ? 'action-button-primary hidden md:inline-flex' : 'action-button-secondary hidden md:inline-flex'}>
              Chat
            </Link>
            {canCreate ? (
              <Link to="/import" className="action-button-secondary hidden md:inline-flex">
                Import
              </Link>
            ) : null}
            <SpaceSwitcher className="action-button-secondary hidden md:inline-flex" />
            {currentUsername ? (
              <UserMenu
                username={currentUsername}
                canAccessAccount={canAccessAccount}
                canManageUsers={canManageUsers}
                onLogout={onLogout}
              />
            ) : (
              <Link to="/login" className="action-button-secondary">
                Login
              </Link>
            )}
          </div>
        </div>
      </header>
      <div className="app-layout__header-spacer" />
      <div className="app-layout__content-wrapper">
        {sidebarVisible ? (
          <>
            <button
              type="button"
              className="app-layout__sidebar-backdrop"
              aria-label="Close sidebar"
              onClick={onToggleSidebar}
            />
            <div className="app-layout__sidebar-container app-layout__sidebar-container--overlay" id="sidebar-container">
            <Sidebar
              tree={tree}
              activePath={activePath}
              openPaths={openPaths}
              canCreate={canCreate}
              canEdit={canEditCurrent || canCreate}
              onNavigate={onNavigate}
              onToggle={onToggleNode}
              onCreate={onCreate}
              onEdit={onEdit}
              onMove={onMove}
              onCopy={onCopy}
              onDelete={onDelete}
              onSort={onSort}
              onConvert={onConvert}
              onExpandAll={onExpandAll}
              onCollapseAll={onCollapseAll}
              onOpenSearch={onOpenSearch}
            />
            </div>
          </>
        ) : null}
        <main className="app-layout__main-content-area custom-scrollbar" id="scroll-container">
          {children}
        </main>
      </div>
    </div>
  )
}
