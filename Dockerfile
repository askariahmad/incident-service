FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY gateway-service/pom.xml gateway-service/
COPY incident-service/pom.xml incident-service/
COPY repo-scanner-service/pom.xml repo-scanner-service/
COPY log-analyzer-service/pom.xml log-analyzer-service/
COPY log-collector-service/pom.xml log-collector-service/
COPY notification-service/pom.xml notification-service/
RUN mvn -pl incident-service -am dependency:go-offline -B
COPY incident-service/src incident-service/src
RUN mvn clean package -pl incident-service -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/incident-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

