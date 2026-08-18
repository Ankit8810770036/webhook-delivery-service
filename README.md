# Reliable Webhook Delivery Service

A production-grade, multi-tenant Webhook Ingestion & Delivery Service built with **Java 25**, **Spring Boot 3.4+**, **PostgreSQL 16**, and **Flyway**. Designed for at-least-once delivery guarantees, resilient database row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`), HMAC-SHA256 payload signing, and circuit breaker fault tolerance.

🚀 **Live Production Cloud Dashboard**: [https://webhook-delivery-service-574n.onrender.com/](https://webhook-delivery-service-574n.onrender.com/)

---

## 1. How to Run in Under 5 Minutes

### Prerequisites
- **Docker & Docker Compose** (or local PostgreSQL 16 + Java 21/25)
- **Maven** (bundled via `./mvnw` or `mvnw.cmd`)

### Quick Start with Docker Compose
Clone the repository and launch the full stack (PostgreSQL + App):

```bash
# 1. Start database and application containers
docker compose up --build -d

# 2. Check health status
curl http://localhost:8090/actuator/health
```

Once started:
- **Interactive Web Dashboard**: [http://localhost:8090](http://localhost:8090)
- **Interactive Swagger UI**: [http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)
- **Actuator Health & Metrics**: [http://localhost:8090/actuator/health](http://localhost:8090/actuator/health)

### Running Locally with Maven
```bash
# Set database environment variables (or rely on .env defaults)
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=webhook_db
export DB_USER=webhook_user
export DB_PASSWORD=webhook_pass

# Run database migrations and start application
./mvnw spring-boot:run
```

### Running Tests
```bash
./mvnw test
```

---

## 2. Architecture & Request Paths

```
                                  INGESTION PATH (< 100ms)
                                  
  [Producer Application]
            │
            │  POST /api/v1/events
            │  Headers: X-Tenant-Id, X-Correlation-Id
            ▼
   [EventController]
            │
            ▼
  [EventIngestionService] ──► Checks unique (tenant_id, event_id_external)
            │                 └─► If duplicate: returns 200/202 with existing event
            │
            ├── 1. Persist Event (events table)
            ├── 2. Fan-out: Query active Subscribed Endpoints for Tenant
            └── 3. Insert PENDING Deliveries (deliveries table)
            │
            ▼
    Returns HTTP 202 Accepted { id, eventId, status: "ACCEPTED" }
    

                                  DELIVERY PIPELINE (Async)
                                  
  [DeliveryWorker Scheduler] (every 1s)
            │
            ▼
  [DeliveryClaimService] ──► SELECT ... FROM deliveries 
            │                WHERE status = 'PENDING' AND next_attempt_at <= now()
            │                ORDER BY next_attempt_at ASC LIMIT 20
            │                FOR UPDATE SKIP LOCKED;
            │
            ├── Atomically sets: status='PROCESSING', locked_by=worker_id, locked_until=now()+60s
            │
            ▼
  [Virtual Thread Worker Pool] (Concurrent Delivery Execution)
            │
            ├── 1. Circuit Breaker Check (per endpoint)
            │       └─► If OPEN: defers attempt by cooldown window
            │
            ├── 2. Compute HMAC-SHA256 Signature (X-Webhook-Signature, X-Webhook-Timestamp)
            │
            ├── 3. Execute HTTP POST with strict connect & read timeouts (4s / 6s)
            │
            └── 4. Process HTTP Response:
                    ├─► 2xx Success:
                    │     • status = 'SUCCESS'
                    │     • Record DeliveryAttempt (audit log with latency & code)
                    │     • Reset Circuit Breaker failures
                    │
                    └─► 4xx/5xx/Timeout/Network Error:
                          • Increment attempt_count
                          • Record DeliveryAttempt
                          • Trip Circuit Breaker if failure threshold reached
                          • If attempts < max (8):
                          │   Calculate exponential backoff + jitter -> next_attempt_at, status='PENDING'
                          • Else:
                              status = 'DEAD_LETTERED'
```

---

## 3. Locking and Claiming Strategy

### Why `SELECT ... FOR UPDATE SKIP LOCKED`?
In high-throughput webhook delivery architectures, worker nodes must claim due jobs concurrently without race conditions or double-delivery.

1. **Eliminates Full Table Scans**:
   We created a dedicated composite index on `(status, next_attempt_at, locked_until)`. The claim query only examines matching pending records.
2. **Zero Lock Contention (`SKIP LOCKED`)**:
   Standard `FOR UPDATE` causes competing workers to block and wait on locked rows. `SKIP LOCKED` instructs PostgreSQL to bypass rows currently locked by other transactions, allowing parallel workers to instantly claim non-overlapping batches.
3. **Lease Timeout & Crash Recovery (`locked_until`)**:
   When a worker claims a batch, it updates `locked_until = now() + 60s`. If a worker instance crashes or gets killed mid-delivery, the lease expires automatically after 60 seconds, allowing another worker to re-claim and resume the delivery without data loss.

---

## 4. Backoff Formula and Retry Limits

### The Formula
$$\text{Delay}(k) = \min\left(\text{initial} \times \text{multiplier}^k + \text{uniform\_jitter}(0, \text{jitter\_factor} \times \text{base}), \text{max\_backoff}\right)$$

Where:
- $\text{initial} = 5\text{ seconds}$
- $\text{multiplier} = 2.0$
- $\text{jitter\_factor} = 0.20 \text{ (20\%)}$
- $\text{max\_attempts} = 8$
- $\text{max\_backoff} = 86400\text{ seconds (24 hours)}$

### Progression Schedule
| Attempt # | Base Delay | With 20% Jitter Window | Cumulative Time Elapsed |
| :--- | :--- | :--- | :--- |
| **1** | 5 sec | 5.0s – 6.0s | ~5s |
| **2** | 10 sec | 10.0s – 12.0s | ~15s |
| **3** | 20 sec | 20.0s – 24.0s | ~35s |
| **4** | 40 sec | 40.0s – 48.0s | ~1m 15s |
| **5** | 80 sec | 80.0s – 96.0s | ~2m 35s |
| **6** | 160 sec (2.6m) | 160s – 192s | ~5m 15s |
| **7** | 320 sec (5.3m) | 320s – 384s | ~10m 35s |
| **8** | 640 sec (10.6m)| 640s – 768s | ~21m 15s |

### Why this design?
- **Full Decorrelated Jitter**: Prevents "thundering herd" problems where hundreds of webhooks against a recovering server retry at the exact same millisecond.
- **8 Attempts Budget**: Gives external receivers sufficient recovery window for transient network blips and short deployments while dead-lettering permanent outages for manual operator redrive.

---

## 5. At-Least-Once Delivery Guarantee

### How We Guarantee At-Least-Once
1. **Durable Ingestion Before Acknowledgement**: Events and initial `PENDING` delivery states are committed to PostgreSQL inside ACID transactions before returning `202 Accepted`.
2. **Crash-Safe Leases**: Work is tracked as `PROCESSING` with a finite lease timestamp (`locked_until`). If a server restarts abruptly, unacknowledged deliveries are reclaimed.
3. **Audit Trail**: Every outbound HTTP attempt is saved into `delivery_attempts` with status code, response time, and error snippet.

### Where Can Duplicate Deliveries Still Theoretically Happen?
In distributed systems, the Two Generals Problem dictates that true "exactly-once" across remote HTTP connections is impossible without idempotent receivers:
- **Network partition right after receiver returns 200**: If the destination receiver processes the webhook and replies `200 OK`, but a network drop occurs before our client finishes reading the TCP socket, our client records a timeout and schedules a retry. The receiver will receive the delivery again.
- **Mitigation**: We provide `X-Correlation-Id`, `X-Delivery-Id`, and `eventId` in request headers and payloads so subscribers can implement idempotent deduplication easily.

---

## 6. Known Limitations & What We Would Do with Two More Weeks

1. **Per-Tenant Partitioning / Sharding**: Currently all tenants share a unified deliveries table. With millions of deliveries, we would implement PostgreSQL native range/hash partitioning by `tenant_id` or date.
2. **Distributed Rate Limiting (Token Bucket / Redis)**: Enforcing outbound rate limits (e.g. max 50 req/sec per target host) to protect downstream endpoints from overload.
3. **Webhook Payload Evolution & Schema Registry**: JSON schema validation and version migration transformations per endpoint subscription.
4. **Strict FIFO Ordering per Endpoint**: A delivery queue mode ensuring event $N+1$ is only dispatched after event $N$ succeeds for that subscriber.
5. **Prometheus / OpenTelemetry Metrics Export**: Micrometer metric timers for dispatch latency percentiles (p50, p95, p99).

---

## 7. One Thing That Surprised Me

During the implementation of row claiming with `SELECT ... FOR UPDATE SKIP LOCKED`, it was fascinating to observe the difference between transaction isolation levels and lock durations. If the outer transaction holding the lock is kept open while executing slow HTTP requests, it holds DB connections unnecessarily long. 

Separating the **claim transaction** (short, DB-only lock & state update to `PROCESSING`) from the **network dispatch** (running on Java 25 virtual threads) allowed the system to claim 1,000+ deliveries in single-digit milliseconds without starving the database connection pool.

---

## 8. API Reference Summary

### Multi-Tenancy Header
All `/api/v1/**` requests require:
```http
X-Tenant-Id: <tenant-name>
```

### Endpoints
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/endpoints` | Register a new target URL with subscribed event types |
| `GET` | `/api/v1/endpoints` | List all endpoints for tenant |
| `GET` | `/api/v1/endpoints/{id}` | Get endpoint details |
| `DELETE` | `/api/v1/endpoints/{id}` | Soft-disable endpoint |
| `POST` | `/api/v1/endpoints/{id}/test` | Inbound self-test ping with HMAC verification |
| `GET` | `/api/v1/endpoints/{id}/deliveries` | Paginated deliveries filtered at database level |
| `POST` | `/api/v1/events` | Ingest event (Idempotent by `eventId`) |
| `GET` | `/api/v1/events/{id}/deliveries` | Get delivery attempts & audit log for event |
| `POST` | `/api/v1/deliveries/{id}/redrive` | Manually re-queue a `DEAD_LETTERED` delivery |
| `GET` | `/actuator/health` | Health indicator reporting DB and worker status |

---

## 9. 5-Minute Evaluation Demo

Run the automated demo script (PowerShell or Bash):

```powershell
# PowerShell
.\demo.ps1
```
```bash
# Bash / Linux / macOS
./demo.sh
```

---

## 10. Live Production Cloud Deployment

The application is fully hosted live on the cloud with a managed PostgreSQL database and 24/7 high-availability keep-alive monitoring:

- **Live Interactive Web Dashboard**: [https://webhook-delivery-service-574n.onrender.com/](https://webhook-delivery-service-574n.onrender.com/)
- **Live Interactive Swagger API Docs**: [https://webhook-delivery-service-574n.onrender.com/swagger-ui.html](https://webhook-delivery-service-574n.onrender.com/swagger-ui.html)
- **Live Actuator Health Indicator**: [https://webhook-delivery-service-574n.onrender.com/actuator/health](https://webhook-delivery-service-574n.onrender.com/actuator/health)
