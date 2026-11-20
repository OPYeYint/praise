# Praise

A private gratitude journal. Write short notes about what you’re glad of, then let them drift back as floating memories.

**Stack:** Spring Boot API · React + Vite client · PostgreSQL · JWT auth

## Suggested GitHub repo name

Use **`praise`**.

It matches the product brand, the Maven artifact (`com.praise:praise`), and the app package. Keep it short and lowercase.

Other options if `praise` is taken:

| Name | When to use |
|------|-------------|
| `praise-journal` | Clearer for discovery / portfolio |
| `praise-app` | Emphasizes the full-stack app |

Avoid vague names like `backend` or `my-project`.

## Features

- Register / sign in with username + password
- Create, edit, and delete gratitude entries (title, body, mood, date)
- Browse your journal
- Summon a random note; notes also appear on a quiet rotating stage

## Project layout

```text
praise/                 Spring Boot API (port 8082)
  src/main/java/…       Auth, JWT, gratitude domain
  src/main/resources/   application.yml, Flyway migrations
  application-local.yml Local secrets (do not commit)
web/                    React client (Vite)
```

## Prerequisites

- Java 17+
- Maven
- Node.js 20+ (and npm)
- PostgreSQL with a database named `praise`

## Setup

### 1. Database

```sql
CREATE DATABASE praise;
```

### 2. Local API config

Create `praise/application-local.yml` (already used by the app; keep it out of git):

```yaml
spring:
  datasource:
    password: "your-postgres-password"
app:
  jwt:
    secret: "replace-with-a-long-random-secret-at-least-32-bytes"
```

Defaults in `praise/src/main/resources/application.yml`:

- JDBC URL: `jdbc:postgresql://localhost:5432/praise`
- Username: `postgres`
- Server port: `8082`
- JWT expiration: `12h`

Flyway runs migrations on startup (`V1`, `V2`).

### 3. Run the API

From `praise/`:

```bash
./mvnw spring-boot:run
```

On Windows (PowerShell), if you use the Maven wrapper or a local Maven install:

```powershell
mvn spring-boot:run
```

API base: `http://localhost:8082`

### 4. Run the web app

From `web/`:

```bash
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8082`. Open the URL Vite prints (usually `http://localhost:5173`).

## API overview

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | No | Create account |
| `POST` | `/api/auth/login` | No | Sign in; returns JWT |
| `GET` | `/api/auth/me` | Bearer | Current user |
| `GET` | `/api/gratitudes` | Bearer | List entries |
| `GET` | `/api/gratitudes/random` | Bearer | Random entry (`204` if none) |
| `GET` | `/api/gratitudes/{id}` | Bearer | Get one |
| `POST` | `/api/gratitudes` | Bearer | Create |
| `PUT` | `/api/gratitudes/{id}` | Bearer | Update |
| `DELETE` | `/api/gratitudes/{id}` | Bearer | Delete |

Send `Authorization: Bearer <token>` on protected routes.

## Scripts

| Where | Command | Purpose |
|-------|---------|---------|
| `praise/` | `mvn spring-boot:run` | Start API |
| `praise/` | `mvn test` | Run API tests |
| `web/` | `npm run dev` | Dev client |
| `web/` | `npm run build` | Production build |
| `web/` | `npm run lint` | Lint |

## Security notes

- Do **not** commit `application-local.yml` or any real DB password / JWT secret.
- Prefer environment-specific secrets outside the repo for anything beyond local development.
