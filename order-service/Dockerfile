# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy root pom and all module poms first (for layer caching)
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/

# Download dependencies (cached unless pom.xml changes)
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline -B --no-transfer-progress

# Copy source code
COPY common-lib/src common-lib/src
COPY order-service/src order-service/src

# Build only order-service (and common-lib as dependency)
RUN mvn clean package -pl common-lib,order-service -am \
    -DskipTests \
    -B \
    --no-transfer-progress

# ─────────────────────────────────────────────
# Stage 2: Runtime (minimal image)
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# Security: run as non-root user
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/order-service/target/order-service-*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 8081

# JVM flags optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

# Health check via Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
