FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY server/mvnw server/pom.xml ./server/
COPY server/.mvn ./server/.mvn
WORKDIR /workspace/server
RUN ./mvnw -B dependency:go-offline

COPY server/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /workspace/server/target/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
