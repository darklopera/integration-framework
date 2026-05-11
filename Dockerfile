FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy parent pom first (caching layer)
COPY pom.xml .
COPY framework/pom.xml framework/
COPY demo/pom.xml demo/

# Copy sources
COPY framework/src framework/src
COPY demo/src demo/src

# Build — install framework first, then build demo
RUN apk add --no-cache maven && \
    mvn install -pl framework -am -q && \
    mvn package -pl demo -am -DskipTests -q

# ── Runtime image ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/demo/target/integration-demo-1.0.0.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dserver.port=${PORT:-8080}", "app.jar"]
