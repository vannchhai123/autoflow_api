FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    ffmpeg \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir edge-tts numpy scipy

COPY scripts ./scripts
COPY storage/templates ./storage/templates

COPY --from=build /app/target/*.jar app.jar

RUN mkdir -p storage/videos storage/audio storage/thumbnails storage/cloned_voices

EXPOSE 8080
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
