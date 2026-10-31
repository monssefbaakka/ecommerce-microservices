# Stage 1: build the jar with the Maven wrapper (JDK only in this stage)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
# Tests are neither compiled nor run here: "mvn verify" runs them outside the image (the ITs need Docker)
RUN ./mvnw -B package -Dmaven.test.skip=true

# Stage 2: run it on a JRE, without Maven or the sources
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
