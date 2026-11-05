# syntax=docker/dockerfile:1

# Build: compile and package the service, then split the jar into layers. Tests are not run here, because they need
# Docker themselves (Testcontainers); they run with ./mvnw verify and in CI.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY api/ api/
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp -DskipTests package \
    && java -Djarmode=tools -jar target/trouble-ticket-api-*.jar extract --layers --launcher --destination extracted

# Run: JRE only, as an unprivileged user. Layers are copied from the least to the most often changed, so a code
# change rebuilds only the last, small layer.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
# 8080: the API. 8081: health probes (Actuator), for the orchestrator only.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
