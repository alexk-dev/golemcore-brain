import { useCallback, useEffect, useMemo, useState } from 'react'
import { FileSearch, Pencil, Search } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { CreatePageDialog } from '../../components/CreatePageDialog'
import { DeleteDialog } from '../../components/DeleteDialog'
import { MoveCopyDialog } from '../../components/MoveCopyDialog'
import { SearchDialog } from '../../components/SearchDialog'
import { SortChildrenDialog } from '../../components/SortChildrenDialog'
import { convertPage, copyPage, createPage, deletePage, getAuthConfig, getConfig, logout, movePage, sortSection } from '../../lib/api'
import { editorPathToRoute, normalizeWikiPath, parentPath, pathToRoute } from '../../lib/paths'
import { useEditorStore } from '../../stores/editor'
import { useTreeStore } from '../../stores/tree'
import { useUiStore } from '../../stores/ui'
import { useViewerStore } from '../../stores/viewer'
import type { CopyPagePayload, CreatePagePayload, MovePagePayload, WikiConfig, WikiNodeKind, WikiPage, WikiTreeNode } from '../../types'
import { AppLayout } from '../layout/AppLayout'
import { PageQuickSwitcherDialog } from '../page-switcher/PageQuickSwitcherDialog'
import { useToolbarActions } from '../toolbar/toolbarStore'

type DialogState =
  | { type: 'none' }
  | { type: 'create'; parentPath: string; kind: 'PAGE' | 'SECTION' }
  | { type: 'move'; path: string }
  | { type: 'copy'; path: string }
  | { type: 'delete'; path: string }
  | { type: 'sort'; page: WikiTreeNode }

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
  const expandAll = useTreeStore((state) => state.expandAll)
  const collapseAll = useTreeStore((state) => state.collapseAll)
  const openAncestorsForPath = useTreeStore((state) => state.openAncestorsForPath)
  const viewerPage = useViewerStore((state) => state.page)
  const editorPage = useEditorStore((state) => state.page)
  const editorInitialPage = useEditorStore((state) => state.initialPage)
  const editorTitle = useEditorStore((state) => state.title)
  const editorSlug = useEditorStore((state) => state.slug)
  const editorContent = useEditorStore((state) => state.content)
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

  const rawRoutePath = useMemo(() => normalizeWikiPath(location.pathname), [location.pathname])
  const isEditorRoute = rawRoutePath === 'e' || rawRoutePath.startsWith('e/')
  const currentPath = isEditorRoute ? rawRoutePath.replace(/^e\/?/, '') : rawRoutePath
  const isLoginRoute = currentPath === 'login'
  const currentPage: WikiPage | null = isEditorRoute ? editorPage : viewerPage
  const createTargetParentPath =
    currentPage?.kind === 'SECTION' ? currentPage.path : currentPage?.parentPath ?? ''
  const canEdit = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'
  const canManageUsers = authDisabled || currentUser?.role === 'ADMIN'
  const canAccessAccount = authDisabled || currentUser !== null
  const isAnonymousPublicReader = !authDisabled && publicAccess && currentUser === null
  const isUtilityRoute = ['login', 'account', 'users', 'import'].includes(currentPath)
  const editorHasUnsavedChanges =
    isEditorRoute &&
    editorPage !== null &&
    editorInitialPage !== null &&
    (editorTitle !== editorInitialPage.title ||
      editorSlug !== editorInitialPage.slug ||
      editorContent !== editorInitialPage.content)
  const canEditCurrentPage = canEdit && !isEditorRoute && !isUtilityRoute && !isAnonymousPublicReader

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

  const setSidebarVisible = useUiStore((state) => state.setSidebarVisible)

  const maybeCollapseOnMobile = useCallback(() => {
    if (typeof window !== 'undefined' && window.innerWidth > 0 && window.innerWidth < 768) {
      setSidebarVisible(false)
    }
  }, [setSidebarVisible])

  const handleNavigate = useCallback((path: string) => {
    openAncestorsForPath(path)
    navigate(pathToRoute(path))
    maybeCollapseOnMobile()
  }, [maybeCollapseOnMobile, navigate, openAncestorsForPath])

  const handleEdit = useCallback((path: string) => {
    openAncestorsForPath(path)
    navigate(editorPathToRoute(path))
    maybeCollapseOnMobile()
  }, [maybeCollapseOnMobile, navigate, openAncestorsForPath])

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
    sourcePath: string,
    payload: MovePagePayload | CopyPagePayload,
  ) => {
    if (!sourcePath) {
      return
    }
    try {
      const nextPage =
        mode === 'move'
          ? await movePage(sourcePath, payload as MovePagePayload)
          : await copyPage(sourcePath, payload as CopyPagePayload)
      await reloadTree()
      handleNavigate(nextPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleDelete = async (sourcePath: string) => {
    if (!sourcePath) {
      return
    }
    try {
      const nextPath = parentPath(sourcePath)
      await deletePage(sourcePath)
      await reloadTree()
      handleNavigate(nextPath)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleSort = async (sourcePath: string, orderedSlugs: string[]) => {
    if (!sourcePath) {
      return
    }
    try {
      await sortSection(sourcePath, orderedSlugs)
      await reloadTree()
      handleNavigate(sourcePath)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const handleConvert = async (sourcePath: string, targetKind: Exclude<WikiNodeKind, 'ROOT'>) => {
    if (!sourcePath) {
      return
    }
    try {
      const nextPage = await convertPage(sourcePath, { targetKind })
      await reloadTree()
      toast.success(`Converted to ${targetKind.toLowerCase()}`)
      handleNavigate(nextPage.path)
    } catch (error) {
      toast.error((error as Error).message)
      throw error
    }
  }

  const toolbarActions = useMemo(() => [
    {
      id: 'quick-switcher',
      label: 'Go to page',
      title: 'Go to page',
      hotkey: 'Mod+Alt+P',
      hotkeyLabel: 'Ctrl+Alt+P',
      icon: <FileSearch size={16} />,
      onRun: () => setQuickSwitcherOpen(true),
    },
    {
      id: 'edit-page',
      label: 'Edit page',
      title: 'Edit page',
      hotkey: 'Mod+E',
      hotkeyLabel: 'Ctrl+E',
      icon: <Pencil size={16} />,
      variant: 'primary' as const,
      hidden: !canEditCurrentPage,
      disabled: !canEditCurrentPage,
      onRun: () => handleEdit(currentPage?.path ?? currentPath),
    },
    {
      id: 'search',
      label: 'Search',
      title: 'Search',
      hotkey: 'Mod+Shift+F',
      hotkeyLabel: 'Ctrl+Shift+F',
      icon: <Search size={16} />,
      onRun: () => setSearchOpen(true),
    },
    {
      id: 'toggle-sidebar',
      label: 'Toggle sidebar',
      title: 'Toggle sidebar',
      hotkey: 'Mod+Shift+E',
      hotkeyLabel: 'Ctrl+Shift+E',
      hidden: true,
      onRun: toggleSidebar,
    },
  ], [canEditCurrentPage, currentPage?.path, currentPath, handleEdit, setQuickSwitcherOpen, setSearchOpen, toggleSidebar])

  useToolbarActions(toolbarActions)

  return (
    <>
      <AppLayout
        siteTitle={config?.siteTitle ?? 'GolemCore Brain'}
        tree={tree}
        activePath={activePath}
        openPaths={openPaths}
        sidebarVisible={sidebarVisible && !isLoginRoute}
        onToggleSidebar={toggleSidebar}
        hideHeader={isLoginRoute}
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
        onEdit={handleEdit}
        onMove={(path) => setDialogState({ type: 'move', path })}
        onCopy={(path) => setDialogState({ type: 'copy', path })}
        onDelete={(path) => setDialogState({ type: 'delete', path })}
        onSort={(page) => setDialogState({ type: 'sort', page })}
        onConvert={(path, targetKind) => void handleConvert(path, targetKind)}
        onExpandAll={expandAll}
        onCollapseAll={collapseAll}
        onOpenSearch={() => setSearchOpen(true)}
        currentUsername={currentUser?.username ?? null}
        canManageUsers={canManageUsers}
        canAccessAccount={canAccessAccount}
        canCreate={canEdit && !isAnonymousPublicReader}
        canEditCurrent={canEditCurrentPage}
        editorTitle={isEditorRoute ? editorTitle : null}
        editorPath={isEditorRoute ? editorPage?.path ?? currentPath : null}
        editorDirty={editorHasUnsavedChanges}
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
        currentPath={dialogState.type === 'move' ? dialogState.path : ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('move', dialogState.type === 'move' ? dialogState.path : '', payload)}
      />

      <MoveCopyDialog
        mode="copy"
        open={dialogState.type === 'copy'}
        currentPath={dialogState.type === 'copy' ? dialogState.path : ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(payload) => handleMoveCopy('copy', dialogState.type === 'copy' ? dialogState.path : '', payload)}
      />

      <DeleteDialog
        open={dialogState.type === 'delete'}
        path={dialogState.type === 'delete' ? dialogState.path : ''}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onConfirm={() => handleDelete(dialogState.type === 'delete' ? dialogState.path : '')}
      />

      <SortChildrenDialog
        open={dialogState.type === 'sort'}
        page={dialogState.type === 'sort' ? dialogState.page : null}
        onOpenChange={(open) => {
          if (!open) {
            setDialogState({ type: 'none' })
          }
        }}
        onSubmit={(orderedSlugs) => handleSort(dialogState.type === 'sort' ? dialogState.page.path : '', orderedSlugs)}
      />
    </>
  )
}
