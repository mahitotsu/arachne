FROM node:22-alpine AS build

WORKDIR /app

ARG BACKEND_ORIGIN=http://localhost:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
ARG CUSTOMER_SERVICE_ORIGIN=http://localhost:8085
ENV CUSTOMER_SERVICE_ORIGIN=$CUSTOMER_SERVICE_ORIGIN
ARG MENU_SERVICE_ORIGIN=http://localhost:8081
ENV MENU_SERVICE_ORIGIN=$MENU_SERVICE_ORIGIN
ARG SUPPORT_SERVICE_ORIGIN=http://localhost:8086
ENV SUPPORT_SERVICE_ORIGIN=$SUPPORT_SERVICE_ORIGIN

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