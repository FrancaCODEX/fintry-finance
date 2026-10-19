FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system fintry && useradd --system --gid fintry fintry
COPY --from=build --chown=fintry:fintry /build/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER fintry
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
