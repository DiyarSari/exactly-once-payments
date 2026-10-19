# idempotent-transfer-api

A money transfer API that **never applies a transfer twice** and **never overdraws an account**, even under retries and heavy concurrency.

[![CI](https://github.com/DiyarSari/idempotent-transfer-api/actions/workflows/ci.yml/badge.svg)](https://github.com/DiyarSari/idempotent-transfer-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## The Problem

Moving money looks like two lines of code: subtract here, add there. In production, two things break it.

**1. Duplicate transfers.** Ayşe sends 250 TRY rent from her mobile app. The request reaches the bank and the money moves, but the response gets lost in a train tunnel. The app times out and **retries automatically**, or Ayşe **taps "Send" again**. Without protection, the landlord gets 500 TRY and Ayşe gets an angry call to customer support.

**2. Race conditions.** Ayşe has 1,000 TRY. Her standing order and a card payment hit the backend at the same millisecond. Both read `balance = 1000`, both check that 800 ≤ 1000, and both subtract. That is a classic *lost update*: the account ends up overdrawn or one debit silently disappears.

This service shows the standard techniques banks and payment providers (Stripe, Adyen, PSD2 APIs) use to stop both.

## How It Works

### 1. Idempotency keys: "the same request means the same result"

Every `POST /api/transfers` carries an `Idempotency-Key` header, a unique value (usually a UUID) that the **client generates once per logical transfer** and reuses on every retry.

| Situation | Result |
|---|---|
| New key | Transfer runs; result stored together with the key |
| Same key + same body | **Stored result returned**, no money moves, header `Idempotent-Replayed: true` |
| Same key + different body | `422 Unprocessable Entity` (a client bug: one key, two different intents) |
| Missing key | `400 Bad Request` |

The key is stored on the transfer row behind a **`UNIQUE` constraint**. If two copies of the same request arrive at exactly the same time, both may miss the first lookup, but only one `INSERT` can win. The loser's transaction rolls back (including its balance changes), and it answers with the winner's stored result.

To tell "same body" from "different body", the service stores a **SHA-256 hash of a canonical form** of the request (`from|to|amount|description`, with the amount normalised to 2 decimals). Formatting differences such as `100` vs `100.00`, whitespace or field order count as the same request.

```mermaid
sequenceDiagram
    autonumber
    participant App as Mobile app
    participant API as Transfer API
    participant DB as PostgreSQL

    App->>API: POST /api/transfers<br/>Idempotency-Key: 7f3c…  {1 → 2, 250.00}
    API->>DB: find transfer by key → none
    API->>DB: BEGIN; debit 1, credit 2, 2 ledger entries,<br/>insert transfer(key, requestHash); COMMIT
    API--xApp: 201 Created (response lost: timeout)

    Note over App: Network timeout → app retries<br/>with the SAME key and body

    App->>API: POST /api/transfers<br/>Idempotency-Key: 7f3c…  {1 → 2, 250.00}
    API->>DB: find transfer by key → found
    API->>API: hash(request) == stored requestHash ✔
    API-->>App: 201 Created, same transfer id<br/>Idempotent-Replayed: true (no money moved)
```

### 2. Optimistic locking with bounded retry: "no lost updates"

`Account` has a JPA `@Version` column. Every balance update runs as

```sql
UPDATE accounts SET balance = ?, version = version + 1 WHERE id = ? AND version = ?
```

If another transaction changed the account in the meantime, zero rows match, Hibernate throws an optimistic-lock exception and **the whole transaction rolls back**. `TransferService` then retries the transfer in a **brand-new transaction** that re-reads fresh balances, up to 5 attempts with exponential backoff and full jitter. If all attempts lose, the client gets `409 Conflict` and can retry safely **with the same Idempotency-Key**.

**Deadlock avoidance.** Accounts are loaded in ascending id order, and `hibernate.order_updates=true` makes Hibernate flush the `UPDATE`s sorted by primary key. Two opposing transfers (A→B and B→A) therefore always lock rows in the same order, so a circular wait cannot happen. A dedicated test fires 40 opposing transfers at once to prove it.

**Defence in depth.** Besides the application check, PostgreSQL enforces `CHECK (balance >= 0)`, so even a bug in the code could not commit an overdraft.

### 3. Double-entry ledger: "money is moved, never created"

Each completed transfer writes exactly **two immutable ledger entries**: a `DEBIT` on the source and a `CREDIT` on the target, for the same amount. All of it (both balance updates, both ledger entries and the transfer record) commits in **one database transaction**, or none of it does.

Invariants that the test suite checks after every test:

- Σ credits − Σ debits = **0** per currency
- every `COMPLETED` transfer has exactly one matching DEBIT and one CREDIT; every `REJECTED` transfer has none
- every account balance = opening balance + its ledger entries
- no balance is negative

## Tech Stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 17, schema managed by Flyway |
| API docs | springdoc-openapi (Swagger UI) |
| Errors | RFC 7807 `ProblemDetail` via a global exception handler |
| Testing | JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers |
| Build / CI | Maven Wrapper, GitHub Actions |

## Quick Start

Prerequisites: **Java 21** and **Docker**.

```bash
git clone https://github.com/DiyarSari/idempotent-transfer-api.git
cd idempotent-transfer-api

docker compose up -d        # PostgreSQL on localhost:5433
./mvnw spring-boot:run      # API on http://localhost:8080
```

- Swagger UI: **http://localhost:8080/swagger-ui.html**
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

A Flyway migration seeds three demo accounts:

| id | Owner | IBAN | Balance |
|---|---|---|---|
| 1 | Ayşe Yılmaz | TR80 9999 9000 0000 0000 0000 01 | 10,000.00 TRY |
| 2 | Mehmet Demir | TR53 9999 9000 0000 0000 0000 02 | 5,000.00 TRY |
| 3 | Zeynep Kaya | TR26 9999 9000 0000 0000 0000 03 | 2,500.00 TRY |

> The container is published on host port **5433** so it does not clash with a locally installed PostgreSQL. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/accounts` | Open an account with an initial balance |
| `GET` | `/api/accounts/{id}` | Account details and balance |
| `GET` | `/api/accounts/{id}/ledger?page=0&size=20` | Ledger entries, newest first (max size 100) |
| `POST` | `/api/transfers` | Transfer money (**requires `Idempotency-Key`**) |
| `GET` | `/api/transfers/{id}` | Transfer details |

### Send the same transfer twice: money moves once

```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f3c2a10-demo-0001' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":250.00,"description":"Rent"}'
```

```http
HTTP/1.1 201
Idempotent-Replayed: false

{"id":"ef669db4-b14d-4a5d-a1af-bb92fe94e533","fromAccountId":1,"toAccountId":2,"amount":250.00,
 "currency":"TRY","description":"Rent","status":"COMPLETED","failureReason":null,"createdAt":"2026-10-19T12:17:41.184324Z"}
```

Run **exactly the same command again** (simulating a retry):

```http
HTTP/1.1 201
Idempotent-Replayed: true

{"id":"ef669db4-b14d-4a5d-a1af-bb92fe94e533", ... "status":"COMPLETED" ...}   ← same transfer id
```

The balances changed only once:

```bash
curl -s http://localhost:8080/api/accounts/1   # "balance": 9750.00  (not 9500.00)
curl -s http://localhost:8080/api/accounts/2   # "balance": 5250.00  (not 5500.00)
```

### Reuse a key with a different body → 422

```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f3c2a10-demo-0001' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":999.00}'
```

```json
{
  "type": "about:blank",
  "title": "Idempotency key reused",
  "status": 422,
  "detail": "Idempotency-Key '7f3c2a10-demo-0001' was already used with a different request body",
  "instance": "/api/transfers",
  "errorCode": "IDEMPOTENCY_KEY_REUSED"
}
```

### Insufficient funds → stored as `REJECTED`

```bash
curl -s -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-0002' \
  -d '{"fromAccountId":3,"toAccountId":1,"amount":99999.00}'
```

```json
{"id":"8ee44a00-…","fromAccountId":3,"toAccountId":1,"amount":99999.00,"currency":"TRY",
 "status":"REJECTED","failureReason":"INSUFFICIENT_FUNDS", ...}
```

### Other calls

```bash
# Open an account (currency defaults to TRY)
curl -s -X POST http://localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Ali Veli","initialBalance":1000.00}'

# Ledger of account 1
curl -s 'http://localhost:8080/api/accounts/1/ledger?page=0&size=10'
```

### Error responses

All errors are RFC 7807 `application/problem+json` with a machine-readable `errorCode`:

| Status | When | `errorCode` |
|---|---|---|
| 400 | Missing `Idempotency-Key`, malformed JSON | `BAD_REQUEST` |
| 400 | Invalid fields (`amount ≤ 0`, more than 2 decimals, `from == to`, ...); field messages in `errors` | `VALIDATION_FAILED` |
| 404 | Account or transfer does not exist | `NOT_FOUND` |
| 409 | Optimistic-lock retries exhausted (safe to retry with the same key) | `CONCURRENT_MODIFICATION` |
| 422 | Idempotency key reused with a different body | `IDEMPOTENCY_KEY_REUSED` |

## Testing

```bash
./mvnw verify     # unit tests + integration tests (needs Docker for Testcontainers)
```

**84 tests**: 67 unit tests (`*Test`, Surefire) and 17 integration tests (`*IT`, Failsafe) against a real PostgreSQL 17 started by Testcontainers. No H2 and no mocked database, so locking and constraints behave exactly as in production.

| Test | What it proves |
|---|---|
| `AccountTest`, `TransferRulesTest` | Debit/credit, overdraft refusal, currency mismatch, scale-2 money |
| `RequestValidationTest` | Bean Validation: positive amounts, 2 decimals, `from ≠ to`, ISO currency |
| `TransferServiceTest` | Retries on optimistic-lock conflict, gives up after N attempts, duplicate-key race returns the winner's result |
| `TransferControllerTest` | HTTP contract: 400 / 409 / 422 problem responses, `Idempotent-Replayed` header |
| `IdempotencyIT` | Same key twice → money moves **once**; same key + different body → **422**; 10 *concurrent* requests with one key → one transfer |
| `ConcurrencyIT` | 50 parallel transfers; opposing transfers without deadlock |
| `LedgerInvariantIT` | Runs **last**; the ledger sums to zero over everything the suite wrote |

### What the concurrency test proves

`ConcurrencyIT.fiftyParallelTransfersNeverOverdrawTheAccount` creates an account with **1,000.00 TRY** and releases **50 threads at once** (a `CountDownLatch` start gate), each sending a 100.00 TRY transfer over real HTTP with its own idempotency key. That is 5,000 TRY of demand against 1,000 TRY of funds.

The test asserts:

- exactly **10 transfers `COMPLETED`** and **40 `REJECTED`** (`INSUFFICIENT_FUNDS`): no more, no fewer
- the source balance ends at exactly **0.00**, never negative
- the targets received exactly 1,000.00 together
- the **total money across all accounts is unchanged**
- the ledger invariants still hold

Without `@Version`, several threads would read the same balance and the test would see more than 10 completions and a negative balance (or a CHECK-constraint failure).

## Design Decisions & Trade-offs

**Optimistic vs pessimistic locking.** Pessimistic locking (`SELECT … FOR UPDATE`) serialises access and never needs retries, but it holds row locks for the whole transaction and is prone to lock queues on hot accounts. Optimistic locking holds no locks while reading and only fails when a real conflict happens, which fits the common case of transfers touching different accounts. The trade-off is retries under heavy contention on a single account; they are bounded (5 attempts with jitter), and the idempotency key makes a client-side retry after a `409` safe. For a few extremely hot accounts (for example a merchant settlement account), `PESSIMISTIC_WRITE` on that path would be the pragmatic switch.

**Why `BigDecimal` (and `NUMERIC(19,2)`).** `0.1 + 0.2` is `0.30000000000000004` in binary floating point. Money must be exact, so amounts are `BigDecimal` end to end, normalised to scale 2 with `RoundingMode.UNNECESSARY`: an amount that would need rounding is an error, never silently changed. Requests with more than 2 decimals are rejected at validation.

**Why store a request hash.** Without it, a client that accidentally reuses a key for a *different* transfer would silently get the old result back and believe its new transfer succeeded. Comparing a SHA-256 of the canonical request turns that bug into an explicit `422`. Hashing the parsed values, not the raw bytes, means harmless formatting differences do not trigger false conflicts, and the stored value has a fixed size whatever the body size.

**Rejections are stored and replayed.** A transfer that fails a business rule (`INSUFFICIENT_FUNDS`, `CURRENCY_MISMATCH`) is persisted as `REJECTED` and returned with `201`. Replaying its key returns the same rejection, even if the account was funded later: the key identifies an *attempt* whose outcome has been decided. Malformed requests (`400`) and unknown accounts (`404`) are *not* stored, so the client can fix the request and reuse the key.

**Retry loop outside the transaction.** `TransferService` (no transaction) loops around `TransferProcessor.execute` (`@Transactional`). Each attempt therefore gets a fresh transaction and persistence context; `spring.jpa.open-in-view` is disabled so no stale entities survive between attempts.

**Opening balances.** Initial deposits are recorded as the account's `opening_balance` instead of as ledger entries against a funding account. This keeps the demo focused on transfers; the reconciliation check `balance = opening_balance + Σ entries` still makes every account auditable.

**Smaller decisions.** Account ids are sequential `BIGINT`s (readable in demos, natural lock ordering); transfer ids are random UUIDs (not guessable). Idempotency keys are globally unique here; a multi-tenant system would scope them per client. IBANs follow the Turkish format with valid mod-97 check digits and a fictional bank code `99999`. Accounts may be opened in any ISO 4217 currency; transfers only run between accounts with the same currency.

## Possible Improvements

- **Transactional outbox**: write a `TransferCompleted` event in the same transaction and publish it to Kafka from a relay, so notifications and fraud checks never miss or double-publish an event.
- **Multi-currency transfers**: FX quotes with expiry, a rate stored on the transfer, and ledger entries in both currencies through a currency-exchange account.
- **Idempotency key expiry**: keep keys for 24–72 hours (as Stripe does) and archive old ones instead of keeping them forever.
- **Authentication and authorisation**: OAuth2 / JWT with Spring Security; only an account's owner can debit it; scope idempotency keys per client.
- **Rate limiting**: per client and per account (Bucket4j or an API gateway) to protect hot accounts and the retry budget.
- **Observability**: Micrometer metrics for retry count, conflicts and replays; distributed tracing with OpenTelemetry.
- **Hot-account strategy**: switch to pessimistic locking or sharded sub-balances for accounts with very high write rates.

## License

[MIT](LICENSE)
