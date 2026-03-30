FROM node:22-alpine AS build

WORKDIR /app

ARG VITE_CASE_SERVICE_BASE_URL=http://localhost:8080
ENV VITE_CASE_SERVICE_BASE_URL=${VITE_CASE_SERVICE_BASE_URL}

COPY marketplace-agent-platform/operator-console/package.json ./
COPY marketplace-agent-platform/operator-console/package-lock.json ./
RUN npm ci

COPY marketplace-agent-platform/operator-console/ ./
RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY marketplace-agent-platform/operator-console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080