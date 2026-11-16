# Build stage — Java 25 matches pom.xml enforcer
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# Run stage
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system applocker && useradd --system --gid applocker applocker \
    && mkdir -p /app/data/lab-ca && chown -R applocker:applocker /app

COPY --from=build /app/target/applocker-backend-*.jar app.jar
USER applocker

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]