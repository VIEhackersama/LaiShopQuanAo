FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system shop && useradd --system --gid shop shop && mkdir /app/media && chown shop:shop /app/media
COPY --from=build /app/target/lai-shop-1.0.0-SNAPSHOT.jar app.jar
USER shop
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
