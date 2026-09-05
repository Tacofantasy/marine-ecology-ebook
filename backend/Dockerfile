FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN groupadd --gid 10001 marine && useradd --uid 10001 --gid marine --no-create-home marine \
    && mkdir -p /app/uploads && chown -R marine:marine /app
COPY --from=build /workspace/target/marine-ebook-api-*.jar /app/app.jar
USER marine
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
