# Sistema de Reclamos Urbanos

TPO de Desarrollo de Aplicaciones II (UADE) — Grupo 10. Docente: Mg. Christian Parkinson.

Los ciudadanos reportan problemas en la vía pública (baches, luminarias rotas, residuos), el sistema
asigna cada reclamo al área municipal que corresponde y los agentes lo resuelven.

Este repositorio contiene el **Trabajo Práctico Primera Parte (Hito 1)**: aplicación empresarial en
capas con los patrones Factory, Repository, Strategy, Observer y Facade.

## Estructura

```text
backend/                    Proyecto Maven multimódulo (Java 21, Spring Boot 3)
  reclamos-comun/           Utilidades: excepciones de negocio y validación
  reclamos-dominio/         Modelo, reglas, fábrica, eventos y contratos. Java puro, sin Spring
  reclamos-persistencia/    Adaptadores JPA de los Repository
  reclamos-app/             Servicios, fachada, eventos y API REST
frontend/                   React + Vite
docs/                       Contrato REST, arquitectura, patrones y evidencias
docker-compose.yml          MySQL para desarrollo
```

Las dependencias entre módulos van en un solo sentido: `app → persistencia → dominio → comun`.

## Requisitos

- JDK 21
- Docker, para MySQL
- Node.js 20 o superior, para el frontend

No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw`).

## Cómo correr los tests

Los tests usan H2 en memoria. No necesitan Docker ni MySQL.

```bash
cd backend
./mvnw test
```

En Windows, `.\mvnw.cmd test`.

## Cómo levantar el backend

```bash
docker compose up -d mysql

cd backend
./mvnw install -DskipTests
./mvnw -pl reclamos-app spring-boot:run
```

La aplicación queda en `http://localhost:8080` con el perfil `dev`.

## Perfiles

| Perfil | Base de datos | Uso |
| --- | --- | --- |
| `dev` (por defecto) | MySQL en Docker, `localhost:3306`, base `reclamos` | Desarrollo y demo |
| `test` | H2 en memoria | Tests automáticos |

La conexión de `dev` se puede cambiar sin tocar archivos con las variables `DB_URL`, `DB_USER` y
`DB_PASSWORD`.

## Configuración de estrategias

Las estrategias activas se eligen en `backend/reclamos-app/src/main/resources/application.yml`:

```yaml
reclamos:
  prioridad:
    estrategia: categoria        # categoria | palabras-clave
  asignacion:
    estrategia: jurisdiccion     # jurisdiccion | carga-trabajo
```

## Documentación

- [Contrato REST](docs/api-contract.md): endpoints, objetos, errores y datos semilla.
- [Entrega de la versión inicial](docs/referencia/entrega-version-inicial.md): documento de la
  primera versión del TP, conservado como referencia para la documentación final.

## Plan de trabajo del Hito 1

| PR | Rama | Contenido |
| --- | --- | --- |
| 1 | `feature/01-domain-foundation` | Estructura, dominio, reglas, Factory y contratos |
| 2 | `feature/02-persistence-repositories` | Persistencia JPA, MySQL y datos semilla |
| 3 | `feature/03-business-rest` | Servicios, Strategy, Observer, Facade y API REST |
| 4 | `feature/04-frontend-integration` | React, integración, documentación y entrega |
