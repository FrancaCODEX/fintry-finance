FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# Windows checkouts may contain CRLF even when Git enforces LF for new checkouts.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && sh ./mvnw -B dependency:go-offline
COPY backend/src src
RUN sh ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system fintry && useradd --system --gid fintry fintry
COPY --from=build --chown=fintry:fintry /build/target/fintry.jar app.jar
USER fintry
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl --fail --silent --max-time 4 http://localhost:8080/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
