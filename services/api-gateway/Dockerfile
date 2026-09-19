# Packages a pre-built jar (run `mvn -pl services/api-gateway -am install -DskipTests`
# from the repo root first). Keeping the Maven build on the host instead of multi-staging it
# in Docker makes local iteration fast - this phase is plumbing, not CI.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/api-gateway-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
