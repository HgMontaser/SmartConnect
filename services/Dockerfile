# syntax=docker/dockerfile:1.7
# One image recipe for every backend module: `docker compose up --build` compiles it (plus
# libs/commons) inside Docker, so the host needs nothing but Docker. Build context is the repo
# root (trimmed by .dockerignore); pick the module with --build-arg MODULE=<path>, e.g.
# services/user-service or examples/demo-app/demo-service.

FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# -Pexamples so modules under examples/ resolve too; the default reactor stays platform-only.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -Pexamples -pl ${MODULE} -am package -DskipTests \
 && cp $(ls ${MODULE}/target/*.jar | grep -v -- '-plain.jar' | head -1) /workspace/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
