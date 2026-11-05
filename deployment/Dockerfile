# syntax=docker/dockerfile:1
# Образ серверного приложения СУДВ. Контекст сборки — корень репозитория:
#   docker build -f deployment/Dockerfile -t sudv-server .

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY . .
# кэш toolchain (CLI, JDK, зависимости Maven) переживает пересборки образа
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin \
    ./kotlin package -m server -f executable-jar \
 && cp build/tasks/_server_executableJarJvm/server-jvm-executable.jar /sudv-server.jar

FROM eclipse-temurin:21-jre-noble
RUN useradd --system sudv
USER sudv
WORKDIR /app
COPY --from=build /sudv-server.jar /app/sudv-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sudv-server.jar"]
