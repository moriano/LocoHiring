# Built from the repository root (see docker-compose.yml) so Maven sees the whole multi-module project.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl LocoHiringService/service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/LocoHiringService/service/target/locohiring-service-*.jar app.jar
EXPOSE 8080 5080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
