# Stage 1: build the jar with the Maven wrapper (JDK only in this stage)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
# Own layer: dependencies are downloaded again only when pom.xml changes, not on every code change
RUN ./mvnw -B dependency:go-offline

COPY src src
# Tests are neither compiled nor run here: "mvn verify" runs them outside the image (the ITs need Docker)
RUN ./mvnw -B package -Dmaven.test.skip=true

# Split the fat jar into its Spring Boot layers: libraries first, our classes last
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Stage 2: run it on a JRE, without Maven or the sources
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run as root: a flaw in the application would not give root access to the container
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# One image layer per Spring Boot layer, from the least to the most often changed:
# a code change only rebuilds and pushes the small "application" layer
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Heap sized from the container memory limit, not from the host RAM.
# Read by the JVM itself: override with "docker run -e JAVA_TOOL_OPTIONS=..."
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# "healthy" = ready to serve requests, database included (readiness probe of Actuator).
# BusyBox wget ships with the Alpine image: no curl to install
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
