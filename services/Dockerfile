# syntax=docker/dockerfile:1.7
# One image recipe for every backend service: `docker compose up --build` compiles the
# service (plus libs/commons) inside Docker, so the host needs nothing but Docker.
# Build context is the repo root; pick the service with --build-arg SERVICE=<name>.

FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /workspace

# Every module's pom is needed for the reactor to resolve, even when building just one.
COPY pom.xml .
COPY libs/commons/pom.xml libs/commons/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/user-service/pom.xml services/user-service/
COPY services/notification-service/pom.xml services/notification-service/

COPY libs/commons/src libs/commons/src
COPY services/${SERVICE}/src services/${SERVICE}/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests \
 && cp services/${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
