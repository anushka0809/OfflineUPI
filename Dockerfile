# Build stage
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app

COPY --from=build /app/target/OfflineUPI-*.jar app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=10s --retries=10 --start-period=60s \
  CMD curl -f http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
