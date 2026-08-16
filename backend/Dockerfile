# Multi-stage build for Render (Docker web service).
#
# Build stage compiles and packages the jar with Maven; nothing from it ends
# up in the final image. Tests are skipped here on purpose (-DskipTests) —
# they already run in Jenkins CI against a real Postgres (ci-postgres), and
# this image has no database available while it's being built, so
# `mvn verify` would just fail here for an unrelated reason (no DB to
# connect to).
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy the pom first so the dependency layer is cached across builds that
# only change application source, not dependencies.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# Runtime stage: JRE only, just the repackaged Spring Boot jar.
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Run as a non-root user rather than the default root.
RUN useradd --system --create-home --shell /usr/sbin/nologin expensewise
COPY --from=build /build/target/*.jar app.jar
RUN chown expensewise:expensewise app.jar
USER expensewise

# Render sets $PORT at runtime; server.port in application.yml already
# honours it (${PORT:8080}). EXPOSE is documentation only, Render ignores it
# and routes to whatever $PORT the process actually binds.
EXPOSE 8080

# TieredStopAtLevel=1 trims JIT warm-up work the JVM would otherwise spend
# tiering through C1/C2 compilation, shortening cold start on Render's
# free/starter tiers where cold starts are the main pain point.
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1"

# SPRING_PROFILES_ACTIVE is set to "prod" via Render's environment
# variables, not baked in here, so the same image could be pointed at a
# different profile without a rebuild if that's ever needed.
ENTRYPOINT ["java", "-jar", "app.jar"]
