# syntax=docker/dockerfile:1
# Contexto de build: services/
#   docker build -f fiapx-processing-worker/Dockerfile -t fiapx/processing-worker:local ..
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY fiapx-contracts/pom.xml /workspace/fiapx-contracts/pom.xml
COPY fiapx-contracts/src /workspace/fiapx-contracts/src
RUN mvn -B -q -f /workspace/fiapx-contracts/pom.xml install -DskipTests

COPY fiapx-processing-worker/pom.xml /workspace/app/
WORKDIR /workspace/app
RUN mvn -B -q dependency:go-offline || true
COPY fiapx-processing-worker/src /workspace/app/src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Traz os patches de seguranca do Alpine (libcrypto3/libssl3/openssl, libexpat) para o Trivy.
RUN apk upgrade --no-cache
RUN apk add --no-cache ffmpeg \
 && addgroup -S fiapx && adduser -S fiapx -G fiapx \
 && mkdir -p /tmp/fiapx && chown fiapx:fiapx /tmp/fiapx
WORKDIR /app
COPY --from=build /workspace/app/target/*.jar app.jar
USER fiapx
EXPOSE 8083
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://localhost:8083/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
