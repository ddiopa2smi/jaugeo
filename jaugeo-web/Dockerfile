# Image du front Jaugeo : application construite par Vite, servie par Nginx qui relaie /api vers l'API.
# Construction : docker build -t jaugeo-web jaugeo-web

FROM node:24-alpine AS construction
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
ENV TZ=Africa/Dakar
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY entetes-securite.conf /etc/nginx/entetes-securite.conf
COPY --from=construction /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/sante || exit 1
