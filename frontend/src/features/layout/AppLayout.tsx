import { Menu, Moon, Sun, UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { PageQuickSwitcherTrigger } from '../page-switcher/PageQuickSwitcherTrigger'
import { Sidebar } from '../sidebar/Sidebar'
import type { WikiTreeNode } from '../../types'

interface AppLayoutProps {
  children: ReactNode
  siteTitle: string
  tree: WikiTreeNode | null
  activePath: string
  openPaths: string[]
  sidebarVisible: boolean
  isDark: boolean
  onToggleTheme: () => void
  onToggleSidebar: () => void
  onNavigate: (path: string) => void
  onToggleNode: (path: string) => void
  onCreate: (parentPath: string, kind: 'PAGE' | 'SECTION') => void
  onOpenSearch: () => void
  onOpenQuickSwitcher: () => void
  currentUsername?: string | null
  canManageUsers: boolean
  onLogout: () => void
}

export function AppLayout({
  children,
  siteTitle,
  tree,
  activePath,
  openPaths,
  sidebarVisible,
  isDark,
  onToggleTheme,
  onToggleSidebar,
  onNavigate,
  onToggleNode,
  onCreate,
  onOpenSearch,
  onOpenQuickSwitcher,
  currentUsername,
  canManageUsers,
  onLogout,
}: AppLayoutProps) {
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
          <div className="app-layout__editor-title-bar-container"></div>
          <div className="app-layout__editor-toolbar-container">
            <PageQuickSwitcherTrigger onOpen={onOpenQuickSwitcher} />
            <button type="button" className="action-button-secondary" onClick={onOpenSearch}>
              Search
            </button>
            <button type="button" className="action-button-secondary" onClick={onToggleTheme}>
              {isDark ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            {currentUsername ? (
              <>
                {canManageUsers ? (
                  <Link to="/users" className="action-button-secondary">
                    <UserRound size={16} />
                    {currentUsername}
                  </Link>
                ) : (
                  <span className="action-button-secondary">
                    <UserRound size={16} />
                    {currentUsername}
                  </span>
                )}
                <button type="button" className="action-button-secondary" onClick={onLogout}>
                  Logout
                </button>
              </>
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
          <div className="app-layout__sidebar-container" id="sidebar-container">
            <Sidebar
              tree={tree}
              activePath={activePath}
              openPaths={openPaths}
              onNavigate={onNavigate}
              onToggle={onToggleNode}
              onCreate={onCreate}
              onOpenSearch={onOpenSearch}
            />
          </div>
        ) : null}
        <main className="app-layout__main-content-area custom-scrollbar" id="scroll-container">
          {children}
        </main>
      </div>
    </div>
  )
}
