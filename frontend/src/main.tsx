import '@fontsource/inter'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'

import App from './App'
import { appBasePath } from './lib/basePath'
import './index.css'

document.documentElement.classList.add('dark')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={appBasePath || undefined}>
      <App />
      <Toaster richColors theme="dark" position="bottom-right" />
    </BrowserRouter>
  </StrictMode>,
)
