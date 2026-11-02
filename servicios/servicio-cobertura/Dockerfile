# Imagen del servicio SOAP de cobertura. El contexto de construcción es la carpeta servicios/
# (docker-compose.yml lo indica): el pom del servicio hereda de servicios/pom.xml, y Maven necesita
# ver todos los módulos que ese pom lista aunque compile solo este.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
RUN mvn -q -DskipTests -pl servicio-cobertura -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/servicio-cobertura/target/servicio-cobertura-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
