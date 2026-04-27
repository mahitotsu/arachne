# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${MAVEN_IMAGE} AS dependencies

WORKDIR /workspace

COPY pom.xml pom.xml
COPY arachne/pom.xml arachne/pom.xml
COPY food-delivery-demo/pom.xml food-delivery-demo/pom.xml
COPY food-delivery-demo/test-support/pom.xml food-delivery-demo/test-support/pom.xml
COPY food-delivery-demo/customer-service/pom.xml food-delivery-demo/customer-service/pom.xml
COPY food-delivery-demo/support-service/pom.xml food-delivery-demo/support-service/pom.xml
COPY food-delivery-demo/order-service/pom.xml food-delivery-demo/order-service/pom.xml
COPY food-delivery-demo/menu-service/pom.xml food-delivery-demo/menu-service/pom.xml
COPY food-delivery-demo/kitchen-service/pom.xml food-delivery-demo/kitchen-service/pom.xml
COPY food-delivery-demo/delivery-service/pom.xml food-delivery-demo/delivery-service/pom.xml
COPY food-delivery-demo/payment-service/pom.xml food-delivery-demo/payment-service/pom.xml
COPY food-delivery-demo/registry-service/pom.xml food-delivery-demo/registry-service/pom.xml
COPY food-delivery-demo/hermes-adapter/pom.xml food-delivery-demo/hermes-adapter/pom.xml
COPY food-delivery-demo/idaten-adapter/pom.xml food-delivery-demo/idaten-adapter/pom.xml

RUN --mount=type=cache,id=arachne-delivery-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp -f food-delivery-demo/pom.xml \
		-pl customer-service,menu-service,kitchen-service,delivery-service,payment-service,order-service,registry-service,hermes-adapter,idaten-adapter \
		-am dependency:go-offline -DskipTests

FROM dependencies AS build

ARG MODULE_DIR
WORKDIR /workspace

COPY . .

RUN --mount=type=cache,id=arachne-delivery-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp -f arachne/pom.xml install -Dmaven.test.skip=true

RUN --mount=type=cache,id=arachne-delivery-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp -f food-delivery-demo/test-support/pom.xml install -Dmaven.test.skip=true

RUN --mount=type=cache,id=arachne-delivery-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp -f ${MODULE_DIR}/pom.xml package -Dmaven.test.skip=true

FROM ${RUNTIME_IMAGE} AS runtime

ARG MODULE_DIR
ARG MODULE_ARTIFACT
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/${MODULE_DIR}/target/${MODULE_ARTIFACT}-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]