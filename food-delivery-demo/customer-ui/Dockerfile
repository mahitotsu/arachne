FROM node:22-alpine AS build

WORKDIR /app

ARG BACKEND_ORIGIN=http://localhost:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN

COPY food-delivery-demo/customer-ui/package*.json ./
RUN --mount=type=cache,id=food-delivery-customer-ui-npm,target=/root/.npm,sharing=locked npm ci

COPY food-delivery-demo/customer-ui ./
RUN npm run build

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME=0.0.0.0

FROM node:22-alpine AS runtime

WORKDIR /app

COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

CMD ["node", "server.js"]