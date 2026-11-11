# Article Management API

Small Java / Spring Boot interview code sample for managing HTML article drafts and published articles.

**Status:** Stage 1 — project skeleton, PostgreSQL via Docker Compose, Flyway, and env-based DB config. Article endpoints are intentionally not implemented yet.

## Stack

- Java 21
- Spring Boot 3.4 (Spring MVC, Spring Data JPA)
- Maven
- PostgreSQL 16
- Flyway (with `flyway-database-postgresql`)
- Docker Compose (local database only)

## Out of scope (documented)

- **Authentication / authorization** — not implemented; any client can call the API once endpoints exist.
- Frontend, media uploads, cloud deployment, real AI summarization, and microservices.

## Prerequisites

- JDK 21+
- Maven 3.9+ (3.8+ works)
- Docker + Docker Compose

## Quick start

### 1. Start PostgreSQL

```bash
docker compose up -d
```

Defaults (overridable via environment or a local `.env` file):

| Variable | Default |
|----------|---------|
| `POSTGRES_DB` | `articles` |
| `POSTGRES_USER` | `articles` |
| `POSTGRES_PASSWORD` | `articles` |
| `POSTGRES_PORT` | `5432` |

Wait until healthy:

```bash
docker compose ps
```

### 2. Run the application

The app reads DB settings from environment variables (with local defaults that match Compose):

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/articles` |
| `DB_USERNAME` | `articles` |
| `DB_PASSWORD` | `articles` |
| `SERVER_PORT` | `8080` |

```bash
# using defaults that match docker-compose.yml
mvn spring-boot:run
```

Custom example:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/articles
export DB_USERNAME=articles
export DB_PASSWORD=articles
mvn spring-boot:run
```

On startup you should see Flyway apply `V1__baseline.sql` and the app listen on port 8080.

### 3. Stop the database

```bash
docker compose down
# remove the volume as well:
docker compose down -v
```

## Project layout

```
src/main/java/com/balanced/articles/   # application entry (feature packages come later)
src/main/resources/application.yml     # datasource + Flyway config
src/main/resources/db/migration/       # Flyway SQL migrations
docker-compose.yml                     # local PostgreSQL
```

Packages will be organized **by feature** (for example `article`) as endpoints are added. No unnecessary abstraction layers.

## Design notes (Stage 1)

- **Env-based DB config** keeps secrets and hostnames out of committed files; defaults match Compose for a frictionless local loop.
- **Flyway owns the schema**; Hibernate `ddl-auto: validate` so the app fails fast if code and migrations drift.
- **`flyway-database-postgresql`** is required separately from Flyway 10 onward (Spring Boot 3.4’s Flyway line).
- **Baseline migration only** — no article tables yet; later stages will add versioned migrations.

## Next stages (planned)

- Article draft create/edit, publish with validation
- List/retrieve published articles with pagination
- `SummaryProvider` (stub)
- Consistent API error model and meaningful tests
