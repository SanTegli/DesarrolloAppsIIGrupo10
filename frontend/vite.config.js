import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// El navegador siempre llama a /api en el mismo origen y Vite reenvía al backend.
// Así no hace falta configurar CORS en Spring.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/api': { target: env.VITE_BACKEND_URL || 'http://localhost:8080', changeOrigin: true },
  }
  return {
    plugins: [react()],
    server: { port: 5173, proxy },
    preview: { port: 4173, proxy },
    test: {
      environment: 'jsdom',
      setupFiles: './src/pruebas/setup.js',
      restoreMocks: true,
    },
  }
})
