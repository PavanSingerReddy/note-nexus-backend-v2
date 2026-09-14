# 🚀 The Complete Apache Kafka Guide for Note Nexus

Welcome! This guide explains how **Apache Kafka** is designed and integrated into the **Note Nexus Backend**. Whether you are new to event-driven architectures or learning Spring Kafka, this guide breaks down every concept with clear analogies, ASCII diagrams, code walkthroughs, and practical testing instructions.

---

## 📑 Table of Contents
1. [Core Concepts: What is Apache Kafka?](#1-core-concepts-what-is-apache-kafka)
2. [Why Did We Add Kafka to This Application?](#2-why-did-we-add-kafka-to-this-application)
3. [Architecture Overview in Note Nexus](#3-architecture-overview-in-note-nexus)
4. [Topic Design & Partitioning Strategy](#4-topic-design--partitioning-strategy)
5. [Step-by-Step Code Walkthrough](#5-step-by-step-code-walkthrough)
   - [A. Infrastructure: KRaft Mode & Kafka UI (`docker-compose-kafka.yml`)](#a-infrastructure-kraft-mode--kafka-ui)
   - [B. Spring Kafka Configuration (`KafkaConfig.java` & `application.yml`)](#b-spring-kafka-configuration)
   - [C. Producers: Guaranteeing In-Order Delivery (`NoteEventProducer`, `EmailNotificationProducer`)](#c-producers-guaranteeing-in-order-delivery)
   - [D. Consumers: High-Throughput Micro-Batching & OpenSearch Bulk Indexing](#d-consumers-high-throughput-micro-batching--opensearch-bulk-indexing)
   - [E. Error Handling & Dead Letter Topics (DLT)](#e-error-handling--dead-letter-topics-dlt)
6. [Real-World Scenarios Walkthrough](#6-real-world-scenarios-walkthrough)
7. [How to Run & Inspect Locally](#7-how-to-run--inspect-locally)

---

## 1. Core Concepts: What is Apache Kafka?

Kafka is a **distributed, append-only event streaming platform**. Unlike traditional message queues (like RabbitMQ or JMS) that delete messages once read, Kafka acts like a **durable digital ledger** where messages are retained on disk for a configurable period, partitioned across broker nodes, and can be replayed at any time.

### Key Terminology Explained with Simple Analogies

| Kafka Concept | Relational DB Analogy | Real-World Analogy |
| :--- | :--- | :--- |
| **Topic** | Table | A dedicated postal mail channel (e.g. `notes.events.v1`) |
| **Record / Event** | Row / Insert | A sealed letter containing a timestamp, key, and JSON body |
| **Partition** | Shard / Table Partition | Parallel mail chutes for the same channel |
| **Offset** | Auto-incrementing Primary Key | Page number in an immutable ledger |
| **Producer** | Application doing `INSERT` | A sender dropping letters into the mail chute |
| **Consumer** | Continuous `SELECT` loop | A clerk reading letters in sequential page order |
| **Consumer Group**| Parallel worker pool | A team of clerks dividing partitions among themselves |
| **DLT (Dead Letter Topic)**| Failed transaction / Exception table | The "undeliverable mail" sorting bin |

---

## 2. Why Did We Add Kafka to This Application?

Before Kafka, Note Nexus relied on Spring's in-memory `@Async` thread pools. While functional, this had severe architectural vulnerabilities:

```
[BEFORE KAFKA] - Vulnerable to Crashes & Bottlenecks
REST API ──► MySQL (Committed)
                │
                ▼ (In-Memory Spring Event: ThreadPoolTaskExecutor)
                ├──► OpenSearch (Single HTTP write per note; drops on crash/timeout)
                └──► Email Service (Blocks on SMTP; drops permanently if SMTP fails)
```

### The 4 Major Architectural Problems Solved by Kafka:

1. **Zero Data Loss on System Restarts or Outages**:
   - *Before*: If OpenSearch or the backend server restarted while indexing tasks were queued in RAM, those search events were lost forever.
   - *With Kafka*: Every note operation is immediately committed to Kafka's disk-backed log before consumer processing. If OpenSearch is down for hours, events safely queue in Kafka and resume indexing automatically when OpenSearch recovers.

2. **Micro-Batching OpenSearch Ingestion**:
   - *Before*: Each note save fired an individual HTTP REST call to OpenSearch (`indexDocument`). 500 concurrent note writes caused 500 HTTP requests.
   - *With Kafka*: `NoteSearchIndexConsumer` uses `batchKafkaListenerContainerFactory`. It ingests batches (e.g., 50-100 events) and uses OpenSearch's high-speed **`_bulk` API**, slashing network overhead by over 90%.

3. **Resilient Asynchronous Email Delivery**:
   - *Before*: User registration and password reset blocked worker threads trying to talk to external SMTP servers (Outlook/Gmail). Rate limits or network delays caused lost emails.
   - *With Kafka*: The API immediately responds with `200 OK` in < 20ms. The `EmailNotificationConsumer` paces requests, retries on failure with exponential backoff, and routes repeatedly failing messages to `notifications.email.v1.DLT` without losing a single activation link.

4. **Strict Per-User Ordering via Keyed Partitions**:
   - By using `userId` as the Kafka message key, all events for a specific user are guaranteed to land on the **same partition**. Kafka processes partitions in strict sequential order (FIFO), guaranteeing that a note `UPDATE` will never be indexed before its `CREATE`.

---

## 3. Architecture Overview in Note Nexus

```
                              ┌───────────────────────────────────────────────────┐
                              │                 CLIENT / REACT UI                 │
                              └─────────────────────────┬─────────────────────────┘
                                                        │
                                                        ▼
                                      ┌───────────────────────────────────┐
                                      │        Spring Boot REST API       │
                                      │      NoteService / UserService    │
                                      └──────────┬─────────────┬──────────┘
                                                 │             │
                                1. SQL Save / Tx │             │ 2. Domain Event
                                                 ▼             ▼
                                      ┌─────────────────┐ ┌───────────────────────┐
                                      │ MySQL / Postgres│ │ AFTER_COMMIT Listener │
                                      └─────────────────┘ └───────────┬───────────┘
                                                                      │
                                                                      ▼
══════════════════════════════════════════════════════════════════════════════════════════════════════
                                    APACHE KAFKA BROKER (KRaft Mode)
══════════════════════════════════════════════════════════════════════════════════════════════════════
    Topic: notes.events.v1                Topic: notifications.email.v1          Topic: user.audit.v1
    [Key: userId]                         [Key: toEmail]                         [Key: userId]
    Partitions: 3                         Partitions: 3                          Partitions: 3
══════════════════════════════════════════════════════════════════════════════════════════════════════
            │                                           │                                │
            ▼                                           ▼                                ▼
 ┌──────────────────────┐                   ┌──────────────────────┐          ┌──────────────────────┐
 │ NoteSearchIndex-     │                   │ EmailNotification-   │          │ UserAuditConsumer    │
 │ Consumer (Batch)     │                   │ Consumer             │          └──────────┬───────────┘
 └──────────┬───────────┘                   └──────────┬───────────┘                     │
            │                                          │                                 ▼
            │ OpenSearch _bulk API                     │ SMTP Dispatch              Audit Stream /
            ▼                                          ▼ (Retry + Backoff)           Security Logs
 ┌──────────────────────┐                   ┌──────────────────────┐
 │ OpenSearch Cluster   │                   │ Outlook / Gmail SMTP │
 └──────────────────────┘                   └──────────────────────┘
            │                                          │
            ▼ If exhausted retries                     ▼ If permanent failure
 ┌──────────────────────┐                   ┌──────────────────────┐
 │ notes.events.v1.DLT  │                   │ notifications.email  │
 │ (Dead Letter Topic)  │                   │        .v1.DLT       │
 └──────────────────────┘                   └──────────────────────┘
```

---

## 4. Topic Design & Partitioning Strategy

| Topic Name | Partitions | Message Key | Value DTO | Consumer Group | Description |
| :--- | :---: | :--- | :--- | :--- | :--- |
| `notes.events.v1` | 3 | `userId` | `NoteEvent` | `notes-search-indexer-group` | Note lifecycle events (`CREATED`, `UPDATED`, `DELETED`) consumed in batches for OpenSearch sync. |
| `notes.events.v1.DLT` | 1 | `userId` | `NoteEvent` | `notes-search-indexer-dlt-group` | Dead letter topic for inspecting poison pills or unindexable notes. |
| `notifications.email.v1` | 3 | `toEmail` | `EmailNotificationEvent` | `email-notification-group` | Asynchronous email dispatch queue for verification and password reset. |
| `notifications.email.v1.DLT` | 1 | `toEmail` | `EmailNotificationEvent` | `email-notification-dlt-group` | Dead letter topic for failed email deliveries. |
| `user.audit.v1` | 3 | `userId` | `UserAuditEvent` | `user-audit-group` | Audit log stream for registrations, token verifications, and password updates. |

---

## 5. Step-by-Step Code Walkthrough

### A. Infrastructure: KRaft Mode & Kafka UI
The Kafka cluster is defined in `docker-compose-kafka.yml`:
- Uses official **Apache Kafka (`apache/kafka:latest`) in KRaft mode** (no ZooKeeper!).
- Dual listeners:
  - `PLAINTEXT://kafka:9092` for internal Docker networking.
  - `EXTERNAL://localhost:9094` for your Spring Boot application running on the host machine.
- **Kafka UI** running at `http://localhost:8085` provides a full visual dashboard to view topics, messages, consumer offsets, and dead-letter records.

### B. Spring Kafka Configuration (`KafkaConfig.java`)
Located at `com.pavansingerreddy.note.config.KafkaConfig`:
- **Automatic Topic Provisioning**: Automatically registers `NewTopic` beans with 3 partitions and replication factor 1.
- **`batchKafkaListenerContainerFactory`**: Configured with `.setBatchListener(true)` and concurrency = 3 so worker threads can pull and process batches of note records concurrently.
- **`singleKafkaListenerContainerFactory`**: Dedicated single-record factory for operations requiring individual acknowledgements like email sending.
- **`CommonErrorHandler` with `DeadLetterPublishingRecoverer`**: Implements exponential backoff (1s, 2s, 4s). After 3 failed attempts, messages are automatically republished to their corresponding `.DLT` topic.

### C. Producers: Guaranteeing In-Order Delivery
Located in package `com.pavansingerreddy.note.kafka.producer`:
- **`NoteEventProducer`**:
  ```java
  String partitionKey = String.valueOf(event.getUserId());
  kafkaTemplate.send(noteEventsTopic, partitionKey, event);
  ```
  By using `userId` as the key, Kafka hashes all notes belonging to that user to the same partition.

### D. Consumers: High-Throughput Micro-Batching & OpenSearch Bulk Indexing
Located in `com.pavansingerreddy.note.kafka.consumer.NoteSearchIndexConsumer`:
```java
@KafkaListener(
        topics = "${app.kafka.topics.note-events}",
        groupId = "notes-search-indexer-group",
        containerFactory = "batchKafkaListenerContainerFactory"
)
public void consumeNoteEventsBatch(List<NoteEvent> events) {
    List<NoteSearchDocument> documentsToIndex = new ArrayList<>();
    for (NoteEvent event : events) {
        if (event.getEventType() == CREATED || event.getEventType() == UPDATED) {
            documentsToIndex.add(toSearchDocument(event));
        } else if (event.getEventType() == DELETED) {
            noteSearchSyncService.deleteDocument(event.getUserId(), event.getNoteId());
        }
    }
    if (!documentsToIndex.isEmpty()) {
        noteSearchSyncService.bulkIndexDocuments(documentsToIndex);
    }
}
```

### E. Error Handling & Dead Letter Topics (DLT)
If a message cannot be processed (for instance, corrupted data or downstream permanent failure):
1. Consumer retries up to 3 times with exponential backoff.
2. If retries fail, `DeadLetterPublishingRecoverer` intercepts the message and publishes it to `notes.events.v1.DLT` or `notifications.email.v1.DLT`.
3. Dedicated `@KafkaListener` DLT handlers log the exact offset, topic, and payload for operational observability.

---

## 6. Real-World Scenarios Walkthrough

### Scenario 1: A User Creates a New Note
1. Frontend calls `POST /api/notes/create`.
2. `NoteServiceImplementation` saves the note in MySQL / PostgreSQL.
3. Spring's `AFTER_COMMIT` event fires $\rightarrow$ `NoteSearchIndexEventListener` receives the committed entity.
4. Listener calls `NoteEventProducer.publishNoteEvent(event)`.
5. Note is published to `notes.events.v1` partition $H(\text{userId}) \pmod 3$.
6. `NoteSearchIndexConsumer` pulls the batch and pushes to OpenSearch via `_bulk`.
7. Note is immediately searchable across all nodes in the OpenSearch cluster.

### Scenario 2: OpenSearch Cluster Outage
1. OpenSearch goes down for scheduled maintenance.
2. Users continue creating, editing, and deleting notes normally via REST API.
3. The SQL database commits each note, and Kafka durably appends every event to disk.
4. When OpenSearch comes back online, the Kafka consumer automatically picks up where it left off, flushing all buffered notes in micro-batches until lag reaches 0.

### Scenario 3: Email Verification on User Registration
1. User registers via `POST /api/user/register`.
2. User and verification token are saved in the database.
3. `RegistrationCompleteEventListener` immediately publishes an `EmailNotificationEvent` to Kafka and returns `200 OK` to the user in milliseconds.
4. `EmailNotificationConsumer` picks up the event and contacts the SMTP server. If the provider experiences a temporary 429 rate limit, Kafka retries with backoff and sends the email once the rate limit window resets.

---

## 7. How to Run & Inspect Locally

### 1. Start the Kafka Infrastructure
Run Kafka and Kafka UI in the background:
```bash
docker-compose -f docker-compose-kafka.yml up -d
```

Verify the containers are running:
```bash
docker ps
```
You should see:
- `note-nexus-kafka` (ports `9092`, `9094`)
- `note-nexus-kafka-ui` (port `8085`)

### 2. Access the Kafka UI Dashboard
Open your browser and navigate to:
👉 **[http://localhost:8085](http://localhost:8085)**

From the dashboard you can:
- **Topics**: View `notes.events.v1`, `notifications.email.v1`, `user.audit.v1` and their `.DLT` topics.
- **Messages**: Click any topic and inspect real-time JSON payloads, headers, keys, and timestamps.
- **Consumers**: Monitor consumer groups (`notes-search-indexer-group`, `email-notification-group`), active members, and consumer lag.

### 3. Start the Spring Boot Backend
```bash
mvn spring-boot:run
```
On startup, Spring Kafka will connect to `localhost:9094` and verify/create all application topics automatically.

### 4. Stop Kafka Services
```bash
docker-compose -f docker-compose-kafka.yml down
```
To also remove persistent topic volumes:
```bash
docker-compose -f docker-compose-kafka.yml down -v
```
