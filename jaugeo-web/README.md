# jaugeo-web

Application web de Jaugeo (React 19, Vite, Material UI, TanStack Query). Voir le [README du projet](../README.md).

```bash
npm ci
npm run dev        # http://localhost:5173, /api relayé vers http://localhost:8080 (JAUGEO_API_URL)
npm test           # tests Vitest
npm run lint
npm run build
npm run e2e        # parcours navigateur (Edge) sur l'application en marche — JAUGEO_ADMIN_MDP requis
```

Organisation : `src/api` (client HTTP, types, requêtes), `src/auth` (session), `src/app` (thème, routes, mise en page), `src/pages` (écrans), `src/shared` (composants et utilitaires).
