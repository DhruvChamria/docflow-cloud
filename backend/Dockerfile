FROM node:24-alpine AS frontend-build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./
COPY backend/core ./core
COPY backend/local-runtime ./local-runtime
COPY backend/api ./api
COPY backend/pdf-worker ./pdf-worker
COPY backend/office-worker ./office-worker
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package

FROM eclipse-temurin:21-jre-jammy
ARG APP_MODULE=api
RUN if [ "$APP_MODULE" = "pdf-worker" ]; then \
      apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ghostscript && \
      rm -rf /var/lib/apt/lists/*; \
    elif [ "$APP_MODULE" = "office-worker" ]; then \
      apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libreoffice-writer libreoffice-calc libreoffice-impress && \
      rm -rf /var/lib/apt/lists/*; \
    fi
WORKDIR /app
COPY --from=build /src/${APP_MODULE}/target/${APP_MODULE}-0.1.0-SNAPSHOT.jar app.jar
COPY --from=frontend-build /src/frontend/dist /app/static
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
