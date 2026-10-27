FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace
COPY settings.gradle build.gradle gradle.properties ./
COPY mod/build.gradle mod/build.gradle
COPY backend/build.gradle backend/build.gradle
COPY mod/src mod/src
COPY backend/src backend/src

RUN gradle --no-daemon :backend:bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/backend/build/libs/backend-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
