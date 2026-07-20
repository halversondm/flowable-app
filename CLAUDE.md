# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build and run all tests (also enforces 80% JaCoCo coverage gate)
mvn verify

# Run tests only (skips packaging)
mvn test

# Run a single test class
mvn test -Dtest=DeploymentControllerTest

# Run a single test method
mvn test -Dtest=DeploymentControllerTest#deployXml_success

# Package JAR without running tests
mvn package -DskipTests

# Run locally (requires PostgreSQL — see env vars below)
mvn spring-boot:run

# Start the full stack (app + postgres)
docker compose up --build

# Generate JaCoCo coverage report to target/site/jacoco/index.html
mvn jacoco:report
```

## Environment Variables

All datasource coordinates default to `localhost` / `flowable` for local development:

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `flowable` |
| `DB_USER` | `flowable` |
| `DB_PASSWORD` | `flowable` |
| `SERVER_PORT` | `8080` |

## Architecture

Spring Boot 4.0.5 + Flowable 8.0.0 + PostgreSQL. The app exposes a REST API for deploying BPMN process definitions and interacting with running process instances.

### Key wiring

- **Flowable auto-configuration** bootstraps all engine services (`RepositoryService`, `RuntimeService`, `TaskService`, etc.) as Spring beans. Controllers use constructor injection to get them.
- **`flowable.database-schema-update: true`** — Flowable manages its own schema (~50 tables prefixed `ACT_`) on startup. JPA `ddl-auto: validate` applies only to application-owned entities (currently none).
- **BPMN auto-deployment** — any `.bpmn20.xml` file placed in `src/main/resources/processes/` is deployed automatically on startup by Flowable's Spring Boot integration.
- **`spring-boot-starter-kafka`** is a required dependency even if Kafka is not used; Flowable 8's `EventRegistryAutoConfiguration` has a compile-time reference to Spring Boot's Kafka autoconfiguration class.

### Package layout

```
com.halversondm.flowableapp
├── deployment/          # BPMN deployment lifecycle (upload, list, delete)
│   ├── DeploymentController.java
│   └── DeploymentResponse.java   # record; includes nested ProcessDefinitionSummary
└── process/             # Runtime process interaction (start instances, work tasks)
    └── ProcessController.java
```

### Test approach

Controller tests use **standalone MockMvc** (`MockMvcBuilders.standaloneSetup()`) — no Spring context is started. All Flowable services are Mockito mocks. `@MockitoSettings(strictness = Strictness.LENIENT)` is required on `DeploymentControllerTest` because `@BeforeEach` stubs both `addString` and `addInputStream` but each individual test only exercises one path.

`FlowableAppApplicationTests` is the only test that starts a full Spring context; it uses H2 in-memory (via `application-test.yml` in `src/test/resources/`) and the `test` profile.

### JaCoCo / Mockito agent setup

JaCoCo 0.8.13 is required (0.8.12 does not support the Java 24 class file format used by Spring Framework 7). The Surefire plugin is configured to pre-load the Byte Buddy agent alongside JaCoCo's agent to prevent an `IllegalClassFormatException` when both instrument classes concurrently. The 80% instruction coverage minimum is enforced as a build gate in `mvn verify`; `FlowableAppApplication` is excluded from the check.

### CI / Docker

GitHub Actions (`build.yml`) runs `mvn verify` then builds and pushes a Docker image to `ghcr.io/halversondm/flowable-app`. The image is tagged with the branch name, `sha-<commit>`, and `latest` (on `main` only). No secrets beyond the built-in `GITHUB_TOKEN` are required. The Dockerfile uses a multi-stage build (Maven build → JRE runtime), runs as a non-root user, and passes `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` so the JVM respects container memory limits.

### Runtime URLs

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/api-docs` | Raw OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Liveness / readiness probe |
