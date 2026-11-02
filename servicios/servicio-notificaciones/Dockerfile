# Imagen del servicio de notificaciones, el consumidor de RabbitMQ. No publica puertos.
# El contexto de construcción es la carpeta servicios/ (docker-compose.yml lo indica): el pom del servicio
# hereda de servicios/pom.xml, y Maven necesita ver todos los módulos que ese pom lista aunque compile solo este.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
RUN mvn -q -DskipTests -pl servicio-notificaciones -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/servicio-notificaciones/target/servicio-notificaciones-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
