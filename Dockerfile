# syntax=docker/dockerfile:1.7

# The build stage does not depend on MODULE, so Docker builds it once and every service image reuses it.
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre AS runtime
ARG MODULE
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]