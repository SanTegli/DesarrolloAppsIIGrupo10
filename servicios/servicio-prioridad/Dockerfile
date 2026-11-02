# Imagen del servicio de prioridad con IA (REST 2). El contexto de construcción es la carpeta servicios/
# (docker-compose.yml lo indica): el pom del servicio hereda de servicios/pom.xml, y Maven necesita
# ver todos los módulos que ese pom lista aunque compile solo este.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
RUN mvn -q -DskipTests -pl servicio-prioridad -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/servicio-prioridad/target/servicio-prioridad-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
