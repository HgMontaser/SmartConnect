# Builds and serves the app entirely inside Docker. VITE_* values are baked in at build time;
# set as env vars they take precedence over any .env* file in the source tree.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_KEYCLOAK_URL=http://localhost:8080
ARG VITE_GATEWAY_HTTP_URL=http://localhost:8090
ARG VITE_GATEWAY_WS_URL=ws://localhost:8090
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_GATEWAY_HTTP_URL=$VITE_GATEWAY_HTTP_URL \
    VITE_GATEWAY_WS_URL=$VITE_GATEWAY_WS_URL
RUN npm run build

FROM docker.io/nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
