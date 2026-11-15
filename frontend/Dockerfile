# Stage 1: build the static files with Node (npm, the sources and node_modules only in this stage)
FROM node:24-alpine AS build
WORKDIR /workspace

# Own layer: the dependencies are installed again only when package.json or package-lock.json change.
# "npm ci" installs exactly what package-lock.json lists, and fails if it does not match package.json
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY index.html tsconfig.json vite.config.ts ./
COPY public public
COPY src src
# Type check, then the Vite build into dist/. The tests run outside the image ("npm test" in the CI)
RUN npm run build

# Stage 2: only the built files, served by nginx. The unprivileged image already runs as the "nginx" user
# (uid 101), never as root, and listens on 8080
FROM nginxinc/nginx-unprivileged:1.30-alpine

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html

# Where nginx sends /api/**: the gateway's name on the Compose network. Override with "-e API_GATEWAY_URL=..."
ENV API_GATEWAY_URL=http://api-gateway:8090
# The entrypoint reads the DNS servers of the container into NGINX_LOCAL_RESOLVERS, used by "resolver"
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

EXPOSE 8080

# BusyBox wget ships with the Alpine image: no curl to install
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/healthz || exit 1
