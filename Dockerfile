ARG BASE_IMAGE=gcr.io/distroless/java25-debian13:nonroot

# ---------- build ----------
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- runtime ----------
FROM ${BASE_IMAGE}
WORKDIR /opt/app
COPY --from=build /app/target/jw-inventory.jar /opt/app/jw-inventory.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/app/jw-inventory.jar"]
