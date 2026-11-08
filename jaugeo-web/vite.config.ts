/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// En développement, /api est relayé vers jaugeo-api : même origine pour le navigateur,
// donc le cookie de rafraîchissement (SameSite=Strict, HttpOnly) fonctionne sans CORS.
const cibleApi = process.env.JAUGEO_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [
    react(),
    // Application installable sur la tablette et chargée sans réseau (coquille mise en cache par le service worker).
    // Les réponses de l'API ne sont jamais mises en cache : données d'exploitation et tablette partagée.
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: false,
      manifest: {
        name: 'Jaugeo — stations-service',
        short_name: 'Jaugeo',
        description: 'Saisie des journées, stocks et trésorerie du réseau de stations',
        lang: 'fr',
        start_url: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#0d47a1',
        icons: [{ src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,woff,woff2}'],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        cleanupOutdatedCaches: true,
      },
    }),
  ],
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
