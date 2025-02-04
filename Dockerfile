# Build stage
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /usr/app
COPY . .
RUN gradle clean build shadowJar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install necessary packages for printing
RUN apk add --no-cache cups libc6-compat openssl

WORKDIR /usr/app
COPY --from=builder /usr/app/build/libs/*.jar ./webapp-hardware-bridge.jar

# Expose any necessary ports
EXPOSE 12212

# Run in server mode by default
ENTRYPOINT ["java", "-jar", "webapp-hardware-bridge.jar"]