ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${MAVEN_IMAGE} AS build

ARG MODULE_DIR
WORKDIR /workspace

COPY . .
RUN mvn -pl ${MODULE_DIR} -am package -DskipTests

FROM ${RUNTIME_IMAGE} AS runtime

ARG MODULE_DIR
ARG MODULE_ARTIFACT
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/${MODULE_DIR}/target/${MODULE_ARTIFACT}-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]