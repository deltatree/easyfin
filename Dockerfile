FROM gradle:8-jdk21 AS builder

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle pom.yaml ./

# Copy source code
COPY src/ src/

# Make gradlew executable and build
RUN chmod +x gradlew && \
    mkdir -p build/staging-deploy && \
    ./gradlew build publishToMavenLocal --no-daemon

# Export stage for copying artifacts
FROM scratch AS export
COPY --from=builder /app/build/libs/ /
COPY --from=builder /app/build/staging-deploy/ /staging-deploy/

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/easyfin-*.jar app.jar

# Create non-root user
RUN addgroup -g 1001 easyfin && \
    adduser -D -s /bin/sh -u 1001 -G easyfin easyfin

USER easyfin

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
