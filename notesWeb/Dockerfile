#BUILD
#Maven JDK 21 for compile code
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

#docker scale cache layer
COPY pom.xml .

#maven down all dependency & save into docker cache layer
RUN mvn dependency:go-offline

#Build source
COPY src ./src
#compile + build JAR
RUN mvn clean package -DskipTests

#RUNTIME
FROM eclipse-temurin:21-jre-alpine
ARG REVISION=local
# Served by /actuator/info so CD can prove the new container answers.
ENV APP_REVISION=$REVISION
LABEL org.opencontainers.image.revision=$REVISION
WORKDIR /app
#jar from stage build
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -q -T 4 -O- "http://127.0.0.1:${SERVER_PORT:-8081}/actuator/health/liveness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
