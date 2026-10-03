# Imagen del backend. Compila dentro del contenedor para no exigir Java ni Maven en la máquina.
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /proyecto
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /proyecto/reclamos-app/target/reclamos-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
