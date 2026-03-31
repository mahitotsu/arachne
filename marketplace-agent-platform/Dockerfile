# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${MAVEN_IMAGE} AS dependencies

WORKDIR /workspace

COPY pom.xml pom.xml
COPY arachne/pom.xml arachne/pom.xml
COPY samples/pom.xml samples/pom.xml
COPY samples/conversation-basics/pom.xml samples/conversation-basics/pom.xml
COPY samples/built-in-tools/pom.xml samples/built-in-tools/pom.xml
COPY samples/secure-downstream-tools/pom.xml samples/secure-downstream-tools/pom.xml
COPY samples/stateful-backend-operations/pom.xml samples/stateful-backend-operations/pom.xml
COPY samples/domain-separation/pom.xml samples/domain-separation/pom.xml
COPY samples/tool-delegation/pom.xml samples/tool-delegation/pom.xml
COPY samples/tool-execution-context/pom.xml samples/tool-execution-context/pom.xml
COPY samples/session-redis/pom.xml samples/session-redis/pom.xml
COPY samples/session-jdbc/pom.xml samples/session-jdbc/pom.xml
COPY samples/approval-workflow/pom.xml samples/approval-workflow/pom.xml
COPY samples/skill-activation/pom.xml samples/skill-activation/pom.xml
COPY samples/streaming-steering/pom.xml samples/streaming-steering/pom.xml
COPY marketplace-agent-platform/pom.xml marketplace-agent-platform/pom.xml
COPY marketplace-agent-platform/case-service/pom.xml marketplace-agent-platform/case-service/pom.xml
COPY marketplace-agent-platform/workflow-service/pom.xml marketplace-agent-platform/workflow-service/pom.xml
COPY marketplace-agent-platform/escrow-service/pom.xml marketplace-agent-platform/escrow-service/pom.xml
COPY marketplace-agent-platform/shipment-service/pom.xml marketplace-agent-platform/shipment-service/pom.xml
COPY marketplace-agent-platform/risk-service/pom.xml marketplace-agent-platform/risk-service/pom.xml
COPY marketplace-agent-platform/notification-service/pom.xml marketplace-agent-platform/notification-service/pom.xml

RUN --mount=type=cache,id=arachne-marketplace-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp \
		-pl marketplace-agent-platform/case-service,marketplace-agent-platform/workflow-service,marketplace-agent-platform/escrow-service,marketplace-agent-platform/shipment-service,marketplace-agent-platform/risk-service,marketplace-agent-platform/notification-service \
		-am dependency:go-offline -DskipTests

FROM dependencies AS build

ARG MODULE_DIR
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,id=arachne-marketplace-m2,target=/root/.m2,sharing=locked \
	mvn -B -ntp -pl ${MODULE_DIR} -am package -DskipTests

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