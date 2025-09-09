FROM maven:3.9.6 AS build
WORKDIR /app
COPY . .
RUN mvn clean package


FROM openjdk:17-jdk
WORKDIR /app
COPY  --from=build /app/target/mail-svc-*.jar app.jar
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]