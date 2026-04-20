FROM node:22-alpine AS build

WORKDIR /app

ARG BACKEND_ORIGIN=http://localhost:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN

COPY marketplace-agent-platform/operator-console/package*.json ./
RUN --mount=type=cache,id=food-delivery-operator-console-npm,target=/root/.npm,sharing=locked npm ci

COPY marketplace-agent-platform/operator-console ./
RUN npm run build

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", ".next/standalone/server.js"]