#BUILD
#Maven JDK 21 for compile code
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

#docker scale cache layer
COPY pom.xml .

#maven down all dependency & save into docker cache layer
RUN mvn dependency:go-offline
COPY src ./src
#compile + build JAR
RUN mvn clean package -DskipTests

#RUNTIME
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
#jar from stage build
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]