# syntax=docker/dockerfile:1
# Imagen del backend. Compila dentro del contenedor para no exigir Java ni Maven en la máquina.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
# Las dependencias de Maven quedan en una caché de BuildKit: la segunda construcción no las vuelve a bajar.
# Las cuatro imágenes Java comparten la caché; "locked" evita que dos Maven escriban a la vez.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/reclamos-app/target/reclamos-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
