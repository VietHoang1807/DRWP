# syntax=docker/dockerfile:1
# Build từ thư mục gốc repo: docker build -f business-app/Dockerfile -t drwp/business-app .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -pl business-app -am package

FROM eclipse-temurin:25-jre
RUN groupadd --system drwp && useradd --system --gid drwp --home /app drwp
WORKDIR /app
COPY --from=build /src/business-app/target/business-app-*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R drwp:drwp /app
USER drwp
EXPOSE 8080 8081 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
