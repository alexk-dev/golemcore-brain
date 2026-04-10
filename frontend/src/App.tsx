import { Navigate, Route, Routes } from 'react-router-dom'

import { LoginPage } from './features/auth/LoginPage'
import { PageEditor } from './features/editor/PageEditor'
import { AccessDeniedPage } from './features/page/AccessDeniedPage'
import { UserManagementPage } from './features/users/UserManagementPage'
import { PageViewer } from './features/viewer/PageViewer'
import { WikiShell } from './features/wiki/WikiShell'
import { useUiStore } from './stores/ui'
import './App.css'

function App() {
  const authDisabled = useUiStore((state) => state.authDisabled)
  const publicAccess = useUiStore((state) => state.publicAccess)
  const currentUser = useUiStore((state) => state.currentUser)
  const canEdit = authDisabled || currentUser?.role === 'ADMIN' || currentUser?.role === 'EDITOR'
  const canView = authDisabled || publicAccess || currentUser !== null
  const canManageUsers = authDisabled || currentUser?.role === 'ADMIN'

  return (
    <WikiShell>
      <Routes>
        <Route path="/login" element={authDisabled ? <Navigate to="/" replace /> : <LoginPage />} />
        <Route path="/users" element={canManageUsers ? <UserManagementPage /> : <Navigate to="/" replace />} />
        <Route path="/" element={canView ? <PageViewer /> : <Navigate to="/login" replace />} />
        <Route path="/e/*" element={canEdit ? <PageEditor /> : <AccessDeniedPage />} />
        <Route path="*" element={canView ? <PageViewer /> : <Navigate to="/login" replace />} />
        <Route path="" element={<Navigate to="/" replace />} />
      </Routes>
    </WikiShell>
  )
}

export default App
