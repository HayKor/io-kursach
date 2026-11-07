# syntax=docker/dockerfile:1
# Build context is the repository root:
#   docker build -f deployment/Dockerfile -t sudv-server .

FROM eclipse-temurin:21-jdk-noble AS build
# pnpm, which the toolchain uses for the web client's npm dependencies, needs libatomic
RUN apt-get update \
 && apt-get install -y --no-install-recommends libatomic1 \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
# keeps the toolchain cache (CLI, JDK, Maven deps) between image builds;
# the web client gets an empty API address: the server serves it, so it calls the API on its own origin
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin \
    ./kotlin package -m server -f executable-jar \
 && cp build/tasks/_server_executableJarJvm/server-jvm-executable.jar /sudv-server.jar \
 && ./kotlin build -m web-app -v release \
 && cp -r build/tasks/_web-app_buildWasmJsAppWasmJsRelease /web \
 && sed -i 's|<meta name="sudv-api" content="[^"]*">|<meta name="sudv-api" content="">|' /web/index.html \
 && grep -q '<meta name="sudv-api" content="">' /web/index.html

FROM eclipse-temurin:21-jre-noble
RUN useradd --system sudv \
 && mkdir -p /srv/sudv/files \
 && chown sudv:sudv /srv/sudv/files
USER sudv
WORKDIR /app
COPY --from=build /sudv-server.jar /app/sudv-server.jar
COPY --from=build /web /app/web
# the built web client, served on / (see WebClientRoutes)
ENV WEB_DIR=/app/web
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sudv-server.jar"]
