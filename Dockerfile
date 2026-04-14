FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN set -eux; \
    for attempt in 1 2 3; do \
        mvn -B -ntp -DskipTests dependency:go-offline && break; \
        if [ "$attempt" -eq 3 ]; then exit 1; fi; \
        sleep 5; \
    done

COPY src ./src
RUN set -eux; \
    for attempt in 1 2 3; do \
        mvn -B -ntp -DskipTests package && break; \
        if [ "$attempt" -eq 3 ]; then exit 1; fi; \
        sleep 5; \
    done \
    && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" target/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --home /app spring \
    && mkdir -p /app/data/documents \
    && chown -R spring:spring /app

ENV APP_DOCUMENT_STORAGE_PATH=/app/data/documents

COPY --from=build --chown=spring:spring /workspace/target/app.jar /app/app.jar

USER spring

EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
