FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# curl 用于 docker-compose 的 healthcheck (actuator/health)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY target/springai-knowledge-server-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
