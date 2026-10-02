# syntax=docker/dockerfile:1.7
# One image, one origin: the PWA build is packaged into the Spring Boot jar and served next to /api.
#   docker build -t ruko .
#   docker run --rm -p 8080:8080 ruko        then open http://localhost:8080
# Sized for a 512 MB instance. Secrets (LLM_*, BHASHINI_*) come from the host's environment, never from this file.

FROM node:24-bookworm-slim AS pwa
WORKDIR /src/pwa
COPY pwa/package.json pwa/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY shared/ /src/shared/
COPY pwa/ ./
RUN npm run build && npm run check:size

FROM eclipse-temurin:21-jdk AS server
WORKDIR /src/server
COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml ./
COPY shared/ /src/shared/
COPY server/src/ src/
COPY --from=pwa /src/pwa/dist/ /src/pwa/dist/
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp package -DskipTests -Dpwa.dist=/src/pwa/dist \
    && java -Djarmode=tools -jar target/ruko-server-*.jar extract --destination /app \
    && mv /app/ruko-server-*.jar /app/ruko.jar

FROM eclipse-temurin:21-jre
# ffmpeg is only needed for spoken input (ASR, off by default): docker build --build-arg WITH_FFMPEG=true
ARG WITH_FFMPEG=false
RUN if [ "$WITH_FFMPEG" = "true" ]; then \
        apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*; \
    fi \
    && useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin ruko
WORKDIR /app
COPY --from=server --chown=root:root /app/ /app/
USER 10001
ENV PORT=8080
EXPOSE 8080
# Heap at 75% of the container limit and the serial collector: the smallest footprint for one small instance.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/ruko.jar"]
