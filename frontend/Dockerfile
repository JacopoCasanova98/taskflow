# syntax=docker/dockerfile:1

FROM node:22.23.2-alpine3.24 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked npm ci

COPY angular.json tsconfig.json tsconfig.app.json ./
COPY src ./src
COPY public ./public
RUN npm run build \
    && test -f dist/taskflow/browser/index.html

FROM nginx:1.30.5-alpine3.24 AS runtime
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /build/dist/taskflow/browser/ /usr/share/nginx/html/
USER nginx
EXPOSE 8080
