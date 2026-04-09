import { Route, Routes } from 'react-router-dom'

import { WikiWorkspace } from './components/WikiWorkspace'
import './App.css'

function App() {
  return (
    <Routes>
      <Route path="*" element={<WikiWorkspace />} />
    </Routes>
  )
}

export default App
