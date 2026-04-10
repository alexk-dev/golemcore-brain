import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { CreatePageDialog } from '../../components/CreatePageDialog'
import { DeleteDialog } from '../../components/DeleteDialog'
import { MoveCopyDialog } from '../../components/MoveCopyDialog'
import { SearchDialog } from '../../components/SearchDialog'
import { SortChildrenDialog } from '../../components/SortChildrenDialog'
import { copyPage, createPage, deletePage, getAuthConfig, getConfig, logout, movePage, sortSection } from '../../lib/api'
import { normalizeWikiPath, parentPath, pathToRoute } from '../../lib/paths'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'
import type { CopyPagePayload, CreatePagePayload, MovePagePayload, WikiConfig, WikiPage } from '../../types'
import { AppLayout } from '../layout/AppLayout'
import { PageQuickSwitcherDialog } from '../page-switcher/PageQuickSwitcherDialog'

type DialogState =
  | { type: 'none' }
  | { type: 'create'; parentPath: string; kind: 'PAGE' | 'SECTION' }
  | { type: 'move' }
  | { type: 'copy' }
  | { type: 'delete' }
  | { type: 'sort' }

interface WikiShellProps {
  children: React.ReactNode
}

export function WikiShell({ children }: WikiShellProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const tree = useTreeStore((state) => state.tree)
  const reloadTree = useTreeStore((state) => state.reloadTree)
  const openNodeIdSet = useTreeStore((state) => state.openNodeIdSet)
  const getPageByPath = useTreeStore((state) => state.getPageByPath)
  const getPageById = useTreeStore((state) => state.getPageById)
  const toggleNode = useTreeStore((state) => state.toggleNode)
  const openAncestorsForPath = useTreeStore((state) => state.openAncestorsForPath)
  const viewerPage = useViewerStore((state) => state.page)
  const editorPage = useEditorStore((state) => state.page)
  const isDark = useUiStore((state) => state.isDark)
  const setDarkMode = useUiStore((state) => state.setDarkMode)
  const sidebarVisible = useUiStore((state) => state.sidebarVisible)
  const toggleSidebar = useUiStore((state) => state.toggleSidebar)
  const searchOpen = useUiStore((state) => state.searchOpen)
  const setSearchOpen = useUiStore((state) => state.setSearchOpen)
  const quickSwitcherOpen = useUiStore((state) => state.quickSwitcherOpen)
  const setQuickSwitcherOpen = useUiStore((state) => state.setQuickSwitcherOpen)
  const authDisabled = useUiStore((state) => state.authDisabled)
  const currentUser = useUiStore((state) => state.currentUser)
  const publicAccess = useUiStore((state) => state.publicAccess)
  const setAuthConfig = useUiStore((state) => state.setAuthConfig)
  const setCurrentUser = useUiStore((state) => state.setCurrentUser)

  const [config, setConfig] = useState<WikiConfig | null>(null)
  const [dialogState, setDialogState] = useState<DialogState>({ type: 'none' })

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark)
  }, [isDark])

  useEffect(() => {
    void reloadTree().catch((error: Error) => toast.error(error.message))
    void getConfig()
      .then((response) => setConfig(response))
      .catch((error: Error) => toast.error(error.message))
    void getAuthConfig()
      .then((response) => setAuthConfig(response))
      .catch((error: Error) => toast.error(error.message))
  }, [reloadTree, setAuthConfig])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = () => setDarkMode(mediaQuery.matches)
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [setDarkMode])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const modifierPressed = event.metaKey || event.ctrlKey
      if (modifierPressed && event.shiftKey && event.key.toLowerCase() === 'f') {
        event.preventDefault()
        setSearchOpen(true)
      }
      if (modifierPressed && event.altKey && event.key.toLowerCase() === 'p') {
        event.preventDefault()
        setQuickSwitcherOpen(true)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [setQuickSwitcherOpen, setSearchOpen])

  const rawRoutePath = useMemo(() => normalizeWikiPath(location.pathname), [location.pathname])
  const isEditorRoute = rawRoutePath.startsWith('e/')
  const currentPath = isEditorRoute ? rawRoutePath.slice(2) : rawRoutePath

  useEffect(() => {
    if (!tree) {
      return
    }
    openAncestorsForPath(currentPath)
  }, [currentPath, openAncestorsForPath, tree])

  const activeTreeNode = getPageByPath(currentPath)
  const activePath = activeTreeNode?.path ?? currentPath
  const openPaths = useMemo(
    () =>
      Object.entries(openNodeIdSet)
        .filter(([, value]) => Boolean(value))
        .map(([id]) => getPageById(id)?.path ?? '')
        .filter((value) => value !== ''),
    [getPageById, openNodeIdSet],
  )

  const currentPage: WikiPage | null = isEditorRoute ? editorPage : viewerPage
  const createTargetParentPath =
    currentPage?.kind === 'SECTION' ? currentPage.path : currentPage?.parentPath ?? ''
  const canEdit = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'
  const canManageUsers = authDisabled || currentUser?.role === 'ADMIN'
  const canAccessAccount = authDisabled || currentUser !== null
  const isAnonymousPublicReader = !authDisabled && publicAccess && currentUser === null

  const handleNavigate = (path: string) => {
    openAncestorsForPath(path)
    navigate(pathToRoute(path))
  }

  const handleLogout = async () => {
    await logout()
    setCurrentUser(null)
    if (!authDisabled) {
      navigate('/login')
    }
  }

  const handleCreate = async (payload: CreatePagePayload) => {
    try {
      const createdPage = await createPage(payload)
      await reloadTree()
      handleNavigate(createdPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleMoveCopy = async (
    mode: 'move' | 'copy',
    payload: MovePagePayload | CopyPagePayload,
  ) => {
    if (!currentPage) {
      return
    }
    try {
      const nextPage =
        mode === 'move'
          ? await movePage(currentPage.path, payload as MovePagePayload)
          : await copyPage(currentPage.path, payload as CopyPagePayload)
      await reloadTree()
      handleNavigate(nextPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleDelete = async () => {
    if (!currentPage) {
      return
    }
    try {
      const nextPath = parentPath(currentPage.path)
      await deletePage(currentPage.path)
      await reloadTree()
      handleNavigate(nextPath)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleSort = async (orderedSlugs: string[]) => {
    if (!currentPage || currentPage.kind === 'PAGE') {
      return
    }
    try {
      await sortSection(currentPage.path, orderedSlugs)
      await reloadTree()
      handleNavigate(currentPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  return (
    <>
      <AppLayout
        siteTitle={config?.siteTitle ?? 'GolemCore Brain'}
        tree={tree}
        activePath={activePath}
        openPaths={openPaths}
        sidebarVisible={sidebarVisible}
        isDark={isDark}
        onToggleTheme={() => setDarkMode(!isDark)}
        onToggleSidebar={toggleSidebar}
        onNavigate={handleNavigate}
        onToggleNode={(path) => {
          const node = getPageByPath(path)
          if (node) {
            toggleNode(node.id)
          }
        }}
        onCreate={(parentPathValue, kind) =>
          setDialogState({ type: 'create', parentPath: parentPathValue || createTargetParentPath, kind })
        }
        onOpenSearch={() => setSearchOpen(true)}
        onOpenQuickSwitcher={() => setQuickSwitcherOpen(true)}
        currentUsername={currentUser?.username ?? null}
        canManageUsers={canManageUsers}
        canAccessAccount={canAccessAccount}
        canCreate={canEdit && !isAnonymousPublicReader}
        onLogout={() => void handleLogout()}
      >
        {children}
      </AppLayout>

      <SearchDialog
        open={searchOpen}
        tree={tree}
        onOpenChange={setSearchOpen}
        onNavigate={handleNavigate}
      />

      <PageQuickSwitcherDialog
        open={quickSwitcherOpen}
        onOpenChange={setQuickSwitcherOpen}
        onNavigate={handleNavigate}
      />

      {canEdit ? (
        <CreatePageDialog
          open={dialogState.type === 'create'}
          parentPath={dialogState.type === 'create' ? dialogState.parentPath : createTargetParentPath}
          kind={dialogState.type === 'create' ? dialogState.kind : 'PAGE'}
          onOpenChange={(open) => {
            if (!open) {
              setDialogState({ type: 'none' })
            }
          }}
          onSubmit={handleCreate}
        />
      ) : null}

      <MoveCopyDialog
        mode="move"
        open={dialogState.type === 'move'}
        currentPath={currentPage?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('move', payload)}
      />

      <MoveCopyDialog
        mode="copy"
        open={dialogState.type === 'copy'}
        currentPath={currentPage?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('copy', payload)}
      />

      <DeleteDialog
        open={dialogState.type === 'delete'}
        path={currentPage?.path ?? ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onConfirm={handleDelete}
      />

      <SortChildrenDialog
        open={dialogState.type === 'sort'}
        page={
          currentPage && currentPage.kind !== 'PAGE'
            ? {
                id: currentPage.id,
                path: currentPage.path,
                parentPath: currentPage.parentPath,
                title: currentPage.title,
                slug: currentPage.slug,
                kind: currentPage.kind,
                hasChildren: currentPage.children.length > 0,
                children: currentPage.children,
              }
            : null
        }
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={handleSort}
      />
    </>
  )
}
