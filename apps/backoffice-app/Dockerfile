# Packages an already-built `dist/` (run `npm run build` first) — same "build on the host,
# package in Docker" pattern as the backend services' Dockerfiles.
FROM docker.io/nginxinc/nginx-unprivileged:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
