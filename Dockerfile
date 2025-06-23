FROM scratch AS export
COPY --from=builder /app/build/libs/ /app/build/libs/
# Copy source code
COPY src/ src/

# Make gradlew executable and build
RUN chmod +x gradlew && \
    ./gradlew build --no-daemon

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
