# syntax=docker/dockerfile:1

# ---- build: compile and package with the Maven wrapper (tests run in CI, not here)
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

# ---- runtime: JRE only, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -H -D app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/credit-card-engine.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
