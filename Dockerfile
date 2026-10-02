# ==============================================================================
# Multi-stage Dockerfile for MemeSpeak backend
#
# Stage 1 (build): Compiles the application with Maven inside a JDK 21 image.
#                  Dependencies are cached in a separate layer for faster builds.
# Stage 2 (runtime): Runs the JAR in a minimal JRE image — no build tools included.
# ==============================================================================

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy only the POM first to cache the dependency download layer.
# Dependencies are re-downloaded only when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline --no-transfer-progress -q

# Copy source and build — skip tests (tests run in CI, not in Docker build)
COPY src ./src
RUN mvn package -DskipTests --no-transfer-progress -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine

# Security: run as a non-root user
RUN addgroup -S memespeak && adduser -S memespeak -G memespeak

WORKDIR /app

# Copy only the executable JAR from the build stage
COPY --from=build /build/target/*.jar app.jar

# Set ownership
RUN chown memespeak:memespeak app.jar

USER memespeak

EXPOSE 8080

# JVM flags:
#   -XX:+UseContainerSupport  — respects Docker CPU/memory limits (on by default in JDK 11+)
#   -XX:MaxRAMPercentage=75.0 — use 75% of container memory for JVM heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
