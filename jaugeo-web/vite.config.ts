/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// En développement, /api est relayé vers jaugeo-api : même origine pour le navigateur,
// donc le cookie de rafraîchissement (SameSite=Strict, HttpOnly) fonctionne sans CORS.
const cibleApi = process.env.JAUGEO_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: cibleApi, changeOrigin: false },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: cibleApi, changeOrigin: false },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Parcours complets (saisie clavier simulée + import différé de la grille) : plus longs qu'un test unitaire.
    testTimeout: 30_000,
  },
})
