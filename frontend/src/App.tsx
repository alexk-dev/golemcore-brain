import { Navigate, Route, Routes } from 'react-router-dom'

import { AccountPage } from './features/account/AccountPage'
import { ApiKeysPage } from './features/api-keys/ApiKeysPage'
import { LoginPage } from './features/auth/LoginPage'
import { PageEditor } from './features/editor/PageEditor'
import { ImportPage } from './features/import/ImportPage'
import { LlmSettingsPage } from './features/llm-settings/LlmSettingsPage'
import { AccessDeniedPage } from './features/page/AccessDeniedPage'
import { ServerErrorPage } from './features/page/ServerErrorPage'
import { SpacesPage } from './features/spaces/SpacesPage'
import { SpaceSettingsPage } from './features/spaces/SpaceSettingsPage'
import { UserManagementPage } from './features/users/UserManagementPage'
import { PageViewer } from './features/viewer/PageViewer'
import { WikiShell } from './features/wiki/WikiShell'
import { useUiStore } from './stores/ui'
import './App.css'

function App() {
  const authDisabled = useUiStore((state) => state.authDisabled)
  const publicAccess = useUiStore((state) => state.publicAccess)
  const currentUser = useUiStore((state) => state.currentUser)
  const authResolved = useUiStore((state) => state.authResolved)
  const canEdit = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'
  const canView = authDisabled || publicAccess || currentUser !== null
  const canManageUsers = authDisabled || currentUser?.role === 'ADMIN'
  const canManageLlmSettings = currentUser?.role === 'ADMIN'
  const canAccessAccount = authDisabled || currentUser !== null
  const needsLoginForEdit = !authDisabled && currentUser === null

  const isStandaloneErrorRoute = window.location.pathname === '/500' || window.location.pathname === '/error'

  if (!authResolved && !isStandaloneErrorRoute) {
    return (
      <WikiShell>
        <div className="shell-form-page" aria-busy="true" />
      </WikiShell>
    )
  }

  return (
    <WikiShell>
      <Routes>
        <Route path="/login" element={authDisabled ? <Navigate to="/" replace /> : <LoginPage />} />
        <Route path="/account" element={canAccessAccount ? <AccountPage /> : <Navigate to="/login" replace />} />
        <Route path="/users" element={canManageUsers ? <UserManagementPage /> : <Navigate to="/" replace />} />
        <Route path="/spaces" element={canManageUsers ? <SpacesPage /> : <Navigate to="/" replace />} />
        <Route path="/spaces/:spaceSlug/settings" element={canManageUsers ? <SpaceSettingsPage /> : <Navigate to="/" replace />} />
        <Route path="/api-keys" element={canManageUsers ? <ApiKeysPage /> : <Navigate to="/" replace />} />
        <Route path="/dynamic-apis" element={<Navigate to="/spaces" replace />} />
        <Route path="/llm-settings" element={canManageLlmSettings ? <LlmSettingsPage /> : <Navigate to="/" replace />} />
        <Route path="/import" element={canEdit ? <ImportPage /> : <Navigate to="/" replace />} />
        <Route path="/500" element={<ServerErrorPage />} />
        <Route path="/error" element={<ServerErrorPage />} />
        <Route path="/" element={canView ? <PageViewer /> : <Navigate to="/login" replace />} />
        <Route
          path="/e/*"
          element={
            canEdit ? (
              <PageEditor />
            ) : needsLoginForEdit ? (
              <AccessDeniedPage
                title="Sign in required"
                message="You must sign in with an editor account to edit pages."
                ctaLabel="Sign in to edit"
                ctaTo="/login"
              />
            ) : (
              <AccessDeniedPage />
            )
          }
        />
        <Route path="*" element={canView ? <PageViewer /> : <Navigate to="/login" replace />} />
        <Route path="" element={<Navigate to="/" replace />} />
      </Routes>
    </WikiShell>
  )
}

export default App
