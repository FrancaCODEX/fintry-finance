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
RUN groupadd --system fintry && useradd --system --gid fintry fintry
COPY --from=build --chown=fintry:fintry /build/target/fintry.jar app.jar
USER fintry
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
