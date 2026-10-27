FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace
COPY . .

RUN gradle --no-daemon --settings-file render-backend.settings.gradle bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/backend-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
