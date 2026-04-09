import { Navigate, Route, Routes } from 'react-router-dom'

import { PageEditor } from './features/editor/PageEditor'
import { PageViewer } from './features/viewer/PageViewer'
import { WikiShell } from './features/wiki/WikiShell'
import './App.css'

function App() {
  return (
    <WikiShell>
      <Routes>
        <Route path="/" element={<PageViewer />} />
        <Route path="/e/*" element={<PageEditor />} />
        <Route path="*" element={<PageViewer />} />
        <Route path="" element={<Navigate to="/" replace />} />
      </Routes>
    </WikiShell>
  )
}

export default App
