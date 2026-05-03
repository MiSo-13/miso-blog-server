FROM gradle:8.13-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/repositories
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8010
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
