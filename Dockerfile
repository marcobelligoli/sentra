# syntax=docker/dockerfile:1

# --- Build: compiles the application and splits the jar into layers ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so they are downloaded again only when the pom changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests need Docker (Testcontainers): run them in CI, not inside the image build
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests \
    && cp target/sentra-*.jar target/sentra.jar \
    && java -Djarmode=tools -jar target/sentra.jar extract --layers --destination target/extracted

# --- Runtime: JRE only, non-root user ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system sentra && useradd --system --gid sentra --no-create-home sentra

# From the least to the most frequently changing layer, to reuse cached layers on redeploys
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER sentra
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "sentra.jar"]
