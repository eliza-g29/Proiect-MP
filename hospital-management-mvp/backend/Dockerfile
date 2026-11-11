FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY Healthcheck.java .
COPY src ./src
RUN mvn -q package
RUN javac --release 21 -d /build/health Healthcheck.java
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/hospital-api-0.1.0.jar /app/app.jar
COPY --from=builder /build/health/Healthcheck.class /app/Healthcheck.class
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD ["java","-cp","/app","Healthcheck"]
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
