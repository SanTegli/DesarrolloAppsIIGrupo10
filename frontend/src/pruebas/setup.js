import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import { TOKEN_CSRF } from './apiFalsa.js'

// Como el backend, deja la cookie del token CSRF que la interfaz manda en cada POST y PATCH.
beforeEach(() => {
  document.cookie = `XSRF-TOKEN=${TOKEN_CSRF}; path=/`
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})
