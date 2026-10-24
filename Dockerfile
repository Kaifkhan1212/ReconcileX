# Multi-stage Dockerfile for ReconcileX Payment Reconciliation Engine

# -------------------------------------------------------------
# Stage 1: Build Application & Cache Dependencies
# -------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for dependency cache layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and grant execution permission
RUN tr -d '\r' < mvnw > mvnw.clean && mv mvnw.clean mvnw && chmod +x mvnw

# Pre-fetch project dependencies to leverage Docker layer caching
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and package application
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# -------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Create unprivileged non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built artifact from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown appuser:appgroup app.jar

# Run as non-root
USER appuser

# Expose default application port
EXPOSE 8080

# Container healthcheck querying the public /api/health endpoint
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=25s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT:-8080}/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
