# syntax=docker/dockerfile:1
# Imagen del servicio SOAP de cobertura. El contexto de construcción es la carpeta servicios/
# (docker-compose.yml lo indica): el pom del servicio hereda de servicios/pom.xml, y Maven necesita
# ver todos los módulos que ese pom lista aunque compile solo este.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
# Las dependencias de Maven quedan en una caché de BuildKit: la segunda construcción no las vuelve a bajar.
# Las cuatro imágenes Java comparten la caché; "locked" evita que dos Maven escriban a la vez.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -DskipTests -pl servicio-cobertura -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/servicio-cobertura/target/servicio-cobertura-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
