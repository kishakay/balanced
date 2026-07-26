# Balanced

**Double-entry ledger API for idempotent transfers, balances, and audit-ready transaction history.**

Balanced is a small, CV-ready portfolio project: a correct double-entry money movement service with retries that do not double-post, balances derived from immutable ledger lines, and a minimal UI to exercise the API.

It is **not** a core banking platform. No KYC, cards, loans, or employer-specific workflows.

---

## Why this exists

Interview and portfolio conversations around payments often stall on slogans (“idempotent”, “ledger”) without a concrete artifact. Balanced is that artifact:

- Every transfer posts a **balanced** journal entry (debits = credits)
- Clients retry safely with an **Idempotency-Key**
- Balances are **computed from ledger lines**, not a mutable balance column
- Voids write a **reversing entry** instead of deleting history
- A React demo makes the behavior visible in minutes

---

## Architecture

```text
┌────────────┐     HTTP + Idempotency-Key      ┌─────────────────────┐
│ React demo │ ───────────────────────────────▶ │ Spring Boot API     │
└────────────┘                                  │  AccountService     │
                                                │  LedgerService      │
                                                └──────────┬──────────┘
                                                           │ JPA / Flyway
                                                           ▼
                                                ┌─────────────────────┐
                                                │ PostgreSQL          │
                                                │ accounts            │
                                                │ journal_entries     │
                                                │ ledger_lines        │
                                                │ idempotency_records │
                                                └─────────────────────┘
```

### Domain model

| Table | Role |
| --- | --- |
| `accounts` | Chart of accounts (`ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`) |
| `journal_entries` | Immutable postings with status `POSTED` / `VOIDED` |
| `ledger_lines` | Debit/credit legs in **integer cents** |
| `idempotency_records` | Maps `Idempotency-Key` → request hash + journal entry |

**Balance rule**

- Debit-normal accounts (`ASSET`, `EXPENSE`): `balance = Σ debit − Σ credit`
- Credit-normal accounts (`LIABILITY`, `EQUITY`, `REVENUE`): `balance = Σ credit − Σ debit`

Voided originals remain in the audit trail; a reversing entry nets the economic effect to zero.

### Idempotency

1. Client sends `Idempotency-Key` on transfers / journal posts / voids
2. Server hashes the request payload
3. Same key + same hash → return the original entry (no second post)
4. Same key + different hash → `409 IDEMPOTENCY_CONFLICT`
5. Unique DB constraints make concurrent retries safe under race

### Stack

- **Backend:** Java 21, Spring Boot 3.4, Spring Data JPA, Validation, Actuator, Flyway
- **Database:** PostgreSQL 16
- **Frontend:** React 19 + Vite + TypeScript
- **Packaging:** Multi-stage `Dockerfile` serves the built UI from the Spring Boot JAR
- **CI:** GitHub Actions (Postgres service + Maven tests + frontend build)

---

## Quick start

### Option A — Docker Compose (recommended demo)

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080).

Demo seed data creates operating cash, reserves, equity opening balances, and a sample transfer.

### Option B — Local API + UI

Prerequisites: Java 21, Maven 3.9+, Node 22+, PostgreSQL 16.

```bash
# database
createdb balanced   # or use docker: docker run --name balanced-pg -e POSTGRES_PASSWORD=balanced -e POSTGRES_USER=balanced -e POSTGRES_DB=balanced -p 5432:5432 -d postgres:16-alpine

# API
./scripts/dev-backend.sh

# UI (separate terminal)
./scripts/dev-frontend.sh
```

- API: `http://localhost:8080`
- UI: `http://localhost:5173` (Vite proxies `/api`)

### Option C — Single JAR with embedded UI

```bash
./scripts/package-ui.sh
cd backend && mvn -DskipTests package
java -jar target/balanced-ledger-1.0.0.jar
```

---

## API

Base path: `/api/v1`

### Accounts

```http
POST /api/v1/accounts
GET  /api/v1/accounts
GET  /api/v1/accounts/{id}
GET  /api/v1/accounts/{id}/balance
GET  /api/v1/accounts/{id}/transactions
POST /api/v1/accounts/{id}/close
```

Create account body:

```json
{
  "code": "ASSET-OPERATING",
  "name": "Operating Cash",
  "accountType": "ASSET",
  "currency": "USD"
}
```

### Transfers (idempotent)

```http
POST /api/v1/transfers
Idempotency-Key: 9f3c2e1a-...
```

```json
{
  "fromAccountId": "...",
  "toAccountId": "...",
  "amountCents": 2500,
  "currency": "USD",
  "description": "Operating → reserves"
}
```

Transfers require matching account types and sufficient available balance. Cross-type postings use journal entries.

### Journal entries

```http
POST /api/v1/journal-entries
GET  /api/v1/journal-entries
GET  /api/v1/journal-entries/{id}
POST /api/v1/journal-entries/{id}/void
```

All mutating journal/transfer/void calls require `Idempotency-Key`.

### Health

```http
GET /actuator/health
```

### Example: fund then transfer

```bash
# create equity + asset (abridged)
curl -s localhost:8080/api/v1/accounts -H 'Content-Type: application/json' \
  -d '{"code":"EQUITY-OPENING","name":"Opening","accountType":"EQUITY","currency":"USD"}'

curl -s localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-transfer-1' \
  -d '{"fromAccountId":"<ops>","toAccountId":"<reserves>","amountCents":1000,"currency":"USD","description":"Demo"}'
```

Retry the same curl: you get the same journal entry id, and balances do not move twice.

---

## Tests & CI

```bash
# requires a Postgres database named balanced_test (user/pass: balanced/balanced)
cd backend && mvn test
```

GitHub Actions (`.github/workflows/ci.yml`) runs:

1. Backend integration tests against a Postgres service container
2. Frontend production build

Coverage focuses on the behaviors that matter in review:

- Happy-path transfer + balance math
- Idempotent replay
- Idempotency conflict on payload mismatch
- Unbalanced entry rejection
- Insufficient funds
- Void + reversing pair + restored balance

---

## Design tradeoffs

| Choice | Why | Tradeoff |
| --- | --- | --- |
| Integer cents | Avoids floating-point money bugs | No multi-decimal currencies (e.g. BTC, some CX) without schema change |
| Derived balances | Auditability; no drift from a cached column | Heavier reads at scale (mitigate with projections / snapshots later) |
| Transfer = same account type | Keeps a simple “move funds” mental model | Cross-type economics use explicit journal entries |
| Reversing voids | Preserves append-only history | Two entries per void (original + reverse) |
| Request-hash idempotency | Detects accidental key reuse with different bodies | Clients must send a stable key per logical operation |
| No auth in v1 | Keeps the demo focused on ledger correctness | Not internet-safe without a gateway / auth layer |
| SPA packaged in JAR | One deployable for demos | Couples UI release to API release |

### Explicitly out of scope

- Full chart-of-accounts product UX
- Multi-currency FX, holds, authorizations
- KYC / AML, cards, loans, interest accrual
- Distributed ledger / event sourcing frameworks
- Anything JPM or employer-specific

---

## Project layout

```text
balanced/
├── backend/                 Spring Boot API + Flyway migrations + tests
├── frontend/                React demo UI
├── scripts/                 local helpers
├── Dockerfile               multi-stage UI + API image
├── docker-compose.yml       Postgres + API
└── .github/workflows/ci.yml
```

---

## Deploy notes

Practical demo targets:

1. **Docker Compose** on any VM / laptop
2. **Render / Railway / Fly.io** — deploy the Docker image, attach a managed Postgres, set:

   - `DATABASE_URL`
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
   - `SEED_DEMO_DATA=true` (optional)

3. **Single JAR** behind any process supervisor when Postgres is already available

There is no paid cloud dependency required to evaluate the project locally.

---

## Author

Built as a public portfolio piece for **Kehinde Olukayode**.

License: MIT (see `LICENSE`).
