# flowable-app

A Spring Boot application integrating [Flowable BPM](https://www.flowable.com/) with PostgreSQL, designed for containerized deployment.

## Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.3.5 |
| Flowable | 7.1.0 |
| PostgreSQL | 16 |

## Prerequisites

- Docker and Docker Compose (for containerized run)
- Java 21 + Maven 3.9 (for local development)

## Running with Docker Compose

The quickest way to get everything running:

```bash
docker compose up --build
```

This starts two containers:
- **postgres** — PostgreSQL 16 with a named volume for persistence
- **app** — the Spring Boot application (waits for Postgres to be healthy before starting)

Once up:
- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/api-docs`
- Health check: `http://localhost:8080/actuator/health`

To stop and remove containers:
```bash
docker compose down
```

To also remove the database volume:
```bash
docker compose down -v
```

## Running Locally (no Docker)

Start a local PostgreSQL instance (or use the compose Postgres service on its own), then:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=flowable
export DB_USER=flowable
export DB_PASSWORD=flowable

mvn spring-boot:run
```

## Environment Variables

All datasource coordinates are injected via environment variables with local-dev defaults:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `flowable` | Database name |
| `DB_USER` | `flowable` | Database username |
| `DB_PASSWORD` | `flowable` | Database password |
| `SERVER_PORT` | `8080` | Application port |

## API Endpoints

### Start a process
```
POST /processes/{processKey}/start
Content-Type: application/json

{ "variableKey": "value" }
```

### List open tasks
```
GET /processes/tasks?assignee={assignee}
```

### Complete a task
```
POST /processes/tasks/{taskId}/complete
Content-Type: application/json

{ "variableKey": "value" }
```

## Adding BPMN Process Definitions

Place `.bpmn20.xml` files under `src/main/resources/processes/`. Flowable auto-deploys them on startup.

## Running Tests

```bash
mvn test
```

Tests use an H2 in-memory database (no external dependencies required).

## Building the JAR

```bash
mvn package -DskipTests
java -jar target/flowable-app-0.0.1-SNAPSHOT.jar
```
