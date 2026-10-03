import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource/public-sans/400.css'
import '@fontsource/public-sans/600.css'
import './estilos.css'
import App from './App.jsx'

createRoot(document.getElementById('raiz')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
