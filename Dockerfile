# Builds both websites and the server into one image. Used by Render (see render.yaml).

# 1. Websites
FROM node:24-slim AS web
WORKDIR /src/frontend
ENV NG_CLI_ANALYTICS=false
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2. Server, with the websites inside its jar
FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /src/backend
COPY backend/pom.xml ./
COPY backend/src src
COPY --from=web /src/frontend/dist /src/frontend/dist
RUN mvn -q -B package -DskipTests
RUN cp target/battery-monitor-*.jar /app.jar && java -Djarmode=tools -jar /app.jar extract --destination /app

# 3. What runs
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=server /app ./
# A practice start that saves the loaded classes, so a small machine starts much faster.
RUN LIFELINE_DASHBOARD_PASSWORD=build LIFELINE_JOIN_CODE=build \
    java -XX:ArchiveClassesAtExit=app.jsa -XX:+UseSerialGC -Dspring.context.exit=onRefresh -jar app.jar \
    --spring.profiles.active=cloud,render --spring.datasource.url=jdbc:h2:mem:build
RUN useradd --system --no-create-home lifeline && mkdir data && chown lifeline data
USER lifeline
ENV SPRING_PROFILES_ACTIVE=cloud,render
ENTRYPOINT ["java", "-Xmx192m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
