# Java 21 E-Commerce Backend Demo - Performance & Concurrency

This is a demonstration of a distributed e-commerce backend intentionally optimized to showcase patterns for handling high concurrency under load. 

It implements a Saga (Choreography) for the Checkout flow, utilizing the Outbox Pattern, Optimistic Locking, and Idempotent Consumers.

## Architecture Highlights
* **Clean Architecture**: Domain, Application, Interfaces, and Infrastructure layers isolated.
* **Saga Choreography**: Distributed transaction without a central orchestrator bottleneck.
* **Outbox Pattern**: Atomically saves domain entities and events in Postgres. A highly tuned relay publishes events to Kafka.
* **Idempotency**: All services track `ProcessedEvent` UUIDs to gracefully handle at-least-once delivery (specifically tested in Payment Service, which emits duplicates 10% of the time).
* **Virtual Threads**: IO-heavy REST endpoints utilize `spring.threads.virtual.enabled=true`.
* **Bounded Parallelism**: Outbox Relay uses a strict platform `ThreadPoolTaskScheduler` to prevent exhausting connection pools. 

---

## 🗄️ Databases (Database-per-Service Pattern)

Each microservice owns its **own isolated PostgreSQL database**. No service can directly query another service's database — cross-service data flow happens exclusively through Kafka events. This is the **Database per Service** pattern, a core principle of microservices architecture.

### 1. `order_db` — Port `5432`
Owned by **order-service**.

| Table | Purpose |
|---|---|
| `orders` | Stores Order entities: `id`, `productId`, `quantity`, `status` (PENDING / COMPLETED / CANCELLED), `idempotencyKey` |
| `outbox_events` | Transactional Outbox — events waiting to be published to Kafka |
| `processed_events` | Tracks already-processed Kafka event IDs (idempotency guard) |

### 2. `inventory_db` — Port `5433`
Owned by **inventory-service**.

| Table | Purpose |
|---|---|
| `inventory` | Stock per product: `productId`, `availableQuantity`, `reservedQuantity`, `version` (optimistic lock) |
| `inventory_reservation` | Maps `orderId → productId + quantity` — used for compensation when payment fails (stock release) |
| `outbox_events` | Transactional Outbox for inventory events |
| `processed_events` | Idempotency tracking |

### 3. `payment_db` — Port `5434`
Owned by **payment-service**.

| Table | Purpose |
|---|---|
| `processed_event` | Idempotency tracking — critical because payment-service intentionally emits duplicate events 10% of the time |

> **Why separate databases?** Each service can be scaled, migrated, or replaced independently. This isolation is also why the **Saga pattern** is necessary — there is no shared SQL transaction across services; consistency is achieved through compensating events.

---

## 🚀 How to Run

### 1. Start Infrastructure
Start Postgres databases, Zookeeper, Kafka, and Prometheus.
```bash
docker compose up -d
```

### 2. Build and Start Services
*Note: Requires Maven to be installed, or you can run `mvnw` if generated.*
```bash
mvn clean install -DskipTests

# Start Order Service (Port 8081)
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar

# Start Inventory Service (Port 8082)
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar

# Start Payment Service (Port 8083)
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
```

Wait until all are connected to Kafka successfully.
Ensure the `inventory` table is seeded containing `PROD1` with stock:
```sql
INSERT INTO inventory (product_id, available_quantity, reserved_quantity, version) VALUES ('PROD1', 1000000, 0, 0);
```

---

## 📊 Performance Runbook

### Running the Load Test (Gatling)
A Gatling simulation is provided to pound the `/orders/checkout` endpoint simulating 200 concurrent users per second.

```bash
cd order-service
mvn gatling:test -Dgatling.simulationClass=com.commerce.order.CheckoutSimulation
```

### Where to read p50/p95/p99 Latency
1. **Gatling HTML Report**: Generated in `order-service/target/gatling/checkoutsimulation-*/index.html`. Open it in any browser to view graphical plots of p50, p75, p95, and p99.
2. **Prometheus / Micrometer Metrics**:
   - Access `http://localhost:8081/actuator/prometheus` (or 8082, 8083)
   - Search for `http_server_requests_seconds_summary` to see `quantile="0.5"`, `0.95`, and `0.99`. Setup Grafana pointing to `localhost:9090` (Prometheus) for visualization.

### Observing CPU, Heap, and GC Pauses
1. **JMX / VisualVM**: Connect to the running JVMs to see real-time GC pauses and Thread dumps. You will clearly see `virtual-thread-*` carriers working efficiently.
2. **Micrometer Default Metrics**:
   - `jvm_memory_used_bytes` (Heap metrics)
   - `jvm_gc_pause_seconds_max` (GC Pause duration)
   - `jvm_threads_live_threads` (Active virtual/platform threads)

### Java Flight Recorder (JFR) Profiling Notes
To capture a profile under load, run the app with JFR enabled:
```bash
# Start recording for 60 seconds of load
jcmd <PID> JFR.start duration=60s filename=profile.jfr settings=profile
```
**What to analyze in Java Mission Control (JMC)**:
- **Thread Contention**: Check for locks in `InventoryCommandService` vs optimistic lock exceptions under `java.lang.Object#wait()`.
- **GC Configuration**: Look at the "Garbage Collections" tab. Since java 21 uses G1GC by default, look for pause time regressions (target is typically 200ms default, adjust `MaxGCPauseMillis` if needed for p99 improvements).
- **Object Allocation**: High allocation rates in the JSON Deserializer can be inspected in the `TLAB Allocations` tab. 

## Invariants & Design Proofs
- **Never Oversell**: The `Inventory` entity uses `@Version` (`optimistic locking`). High concurrency will yield `ObjectOptimisticLockingFailureException` gracefully rather than overselling, acting dynamically as a backpressure mechanism on hot SKUs. 
- **Exactly-Once-ish**: Payment Service mocks "double emits" 10% of the time, yet Order State will only advance once due to the strict `ProcessedEvent` barrier check.
