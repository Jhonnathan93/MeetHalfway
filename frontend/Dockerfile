# syntax=docker/dockerfile:1

# ---- Build stage: install deps and build the Vue SPA ----
# Node 20 LTS satisfies Vite 6 / vue-tsc requirements.
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies from the lockfile for reproducible builds.
COPY package.json package-lock.json ./
RUN npm ci

# Copy sources and produce the production bundle in /app/dist.
COPY . .
RUN npm run build

# ---- Runtime stage: serve the static bundle with nginx on port 80 ----
FROM nginx:1.27-alpine AS runtime

# SPA-aware server config: serve static assets and fall back to index.html so
# client-side routes resolve. This container sits behind the edge nginx, which
# proxies /api/v1 to the backend; here we only serve the built frontend.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
