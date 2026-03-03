# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle --no-daemon clean buildFatJar -x test

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/store-abstract-all.jar /app/app.jar
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
