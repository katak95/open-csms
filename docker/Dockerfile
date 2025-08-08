# Multi-stage Dockerfile for Open-CSMS
# Optimized for production deployment with security best practices

# Build stage
FROM openjdk:17-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew ./
COPY gradle ./gradle/
COPY build.gradle settings.gradle gradle.properties ./

# Copy module build files
COPY modules/*/build.gradle modules/
RUN find modules -name build.gradle -exec dirname {} \; | xargs -I {} mkdir -p {}

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src/
COPY modules ./modules/

# Build application
RUN ./gradlew build -x test --no-daemon

# Production stage
FROM openjdk:17-jre-slim as production

# Create non-root user for security
RUN groupadd -r opencsms && \
    useradd -r -g opencsms -s /bin/false opencsms && \
    mkdir -p /app/logs /app/config /app/certs && \
    chown -R opencsms:opencsms /app

# Install security updates and minimal required packages
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        dumb-init && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Set working directory
WORKDIR /app

# Copy application JAR from builder stage
COPY --from=builder --chown=opencsms:opencsms /app/build/libs/open-csms-*.jar app.jar

# Copy configuration files
COPY --chown=opencsms:opencsms docker/config/ ./config/

# Create security policy file
COPY --chown=opencsms:opencsms docker/security.policy ./security.policy

# Switch to non-root user
USER opencsms:opencsms

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run application with security manager and optimized JVM settings
CMD ["java", \
     "-Djava.security.manager=default", \
     "-Djava.security.policy=security.policy", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+UseG1GC", \
     "-XX:+UseStringDeduplication", \
     "-XX:+OptimizeStringConcat", \
     "-Dspring.profiles.active=production", \
     "-jar", "app.jar"]