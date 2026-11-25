FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY angular.json tsconfig*.json ./
COPY src/ src/
RUN npm run build

FROM nginx:stable-alpine-slim AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/dudu-bubu/browser/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/nginx-health || exit 1
