# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar ----
# Java 25 is required (the app targets release=25 and runs on Spring Boot 3.5.x).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper first so its layer caches independently of source.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copy sources and build. Tests run in CI; skip them here for a fast image build.
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage: minimal JRE with just the jar ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root user for safety.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# The Spring Boot plugin produces a single runnable jar; copy it as app.jar.
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
