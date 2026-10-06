# Backend Interview Project

A hands-on project designed to prepare for **Java Backend / Spring Boot / Microservices interviews**.

The goal is not to build a complete production-ready e-commerce platform. Instead, this project is an **interview playground** where each feature, experiment, and production incident is used to practice and demonstrate backend concepts.

---

## 1. Core Philosophy

Use:

> **1 main project + small focused labs + production incident simulations**

Avoid both extremes:

* ❌ A separate full project for every interview question
* ❌ One huge project containing every possible technology and feature

The main project provides context and realistic interactions between components.

The labs isolate individual concepts so they can be understood and experimented with quickly.

The incident simulations reproduce realistic production problems.

---

## 2. Repository Structure

```text
backend-interview/
│
├── project/
│   └── ecommerce/
│       ├── order-service/
│       ├── product-service/
│       └── inventory-service/
│
├── labs/
│   ├── java/
│   ├── spring/
│   ├── jpa/
│   ├── database/
│   ├── redis/
│   ├── kafka/
│   └── elasticsearch/
│
└── incidents/
    ├── deadlock/
    ├── lost-update/
    ├── overselling/
    ├── retry-storm/
    ├── downstream-timeout/
    ├── duplicate-message/
    └── slow-query/
```

---

## 3. Main Project

The main project is a simplified **e-commerce / order management system**.

Start small.

### Initial services

```text
Client
   │
   ▼
Order Service
   │
   ├── Product Service
   │
   └── Inventory Service
```

Initially, do **not** add Kafka, Redis, Elasticsearch, API Gateway, or other infrastructure.

Add them only when they are needed to demonstrate a specific concept.

---

## 4. First Use Case

Start with only one important use case:

```http
POST /orders
```

Example request:

```json
{
  "productId": 100,
  "quantity": 2
}
```

Initial flow:

```text
Order Service
     │
     ├── Check Product
     │
     ├── Check Inventory
     │
     ├── Create Order
     │
     └── Decrease Stock
```

This simple use case will gradually become the foundation for many interview topics.

---

## 5. Learning Through the Project

Whenever learning a new concept, try to integrate it into the main project.

For example, when learning **Optimistic Locking**:

```java
@Version
private Long version;
```

Create a realistic scenario:

```text
Admin A ── update product ──┐
                            ├── conflict
Admin B ── update product ──┘
```

Then answer:

1. How does it work?
2. Why is it needed?
3. What SQL does Hibernate generate?
4. What happens when versions conflict?
5. What exception is thrown?
6. How should the API handle the conflict?
7. When would pessimistic locking be more appropriate?

The goal is to turn theoretical knowledge into practical interview knowledge.

---

## 6. Labs

Labs are intentionally small and isolated.

Example:

```text
labs/jpa/optimistic-lock/
├── README.md
├── Product.java
├── ProductRepository.java
├── ProductService.java
└── OptimisticLockTest.java
```

Each lab should focus on **one concept**.

Example README:

```text
Question:
How do you prevent two users from overwriting each other's changes?

Concept:
Optimistic Locking

Implementation:
JPA @Version

Failure:
OptimisticLockException

Production Solution:
Return a conflict response and ask the client to refresh/retry.
```

Labs should be executable whenever possible.

---

## 7. Production Incident Simulations

The `incidents/` directory contains deliberately broken scenarios.

Examples:

```text
incidents/
├── deadlock/
├── lost-update/
├── overselling/
├── retry-storm/
├── downstream-timeout/
├── duplicate-message/
└── slow-query/
```

For each incident:

1. Reproduce the problem.
2. Understand why it happens.
3. Identify the symptoms.
4. Implement a solution.
5. Explain the trade-offs.
6. Add tests where appropriate.
7. Document the final solution.

Example:

```text
Client
   │
   ▼
Order Service
   │
   ▼
Payment Service
   X
 timeout
```

Possible topics:

```text
Timeout
   ↓
Retry
   ↓
Exponential Backoff
   ↓
Jitter
   ↓
Idempotency
   ↓
Circuit Breaker
```

---

## 8. Interview Topic Roadmap

### Phase 1 — Java / Database / JPA

```text
Java Collections
Concurrency
Streams
Memory Model

JPA Entity Lifecycle
Persistence Context
Dirty Checking
Lazy Loading
N+1
Transactions
Isolation Levels
Database Indexes
Optimistic Locking
Pessimistic Locking
```

### Phase 2 — Concurrency & Consistency

```text
Race Conditions
Lost Updates
Overselling
Deadlocks
Idempotency
Atomic Operations
Distributed Consistency
```

### Phase 3 — Distributed Systems

```text
Service-to-Service Communication
Timeout
Retry
Exponential Backoff
Jitter
Circuit Breaker
Bulkhead
Distributed Transactions
Eventual Consistency
```

### Phase 4 — Infrastructure

```text
Redis
Kafka
Elasticsearch
API Gateway
Observability
Logging
Metrics
Tracing
```

---

## 9. The Three Interview Questions

Every feature or experiment should help answer three questions:

### How?

> How did you implement it?

### Why?

> Why did you choose this approach instead of the alternatives?

### What if?

> What happens when something goes wrong in production?

For example, for retry:

```text
How?
→ Exponential backoff + jitter

Why?
→ Avoid retry storms

What if?
→ The downstream service is already overloaded?
→ The request is not idempotent?
→ All retries fail?
→ The timeout is too long?
→ Should a circuit breaker be used?
```

This is the level of reasoning expected in backend interviews.

---

## 10. Technology Stack

Start with:

```text
Java 25
Spring Boot
Spring Data JPA
PostgreSQL
Docker Compose
JUnit
Testcontainers
```

Add technologies progressively:

```text
Redis
Kafka
Elasticsearch
API Gateway
Observability
```

Do not introduce a technology unless it helps demonstrate a specific concept.

---

## 11. Guiding Principle

The purpose of this repository is **not**:

> Build the biggest e-commerce application possible.

The purpose is:

> Build a small system that can demonstrate a large number of important backend engineering concepts.

The project should evolve from:

```text
POST /orders
```

into a system that can demonstrate:

```text
JPA
Transaction
Isolation
Locking
Idempotency
Retry
Timeout
Caching
Messaging
Concurrency
Consistency
Performance
Distributed Systems
```

The final repository should serve as both:

* a **hands-on learning environment**
* an **interview preparation toolkit**
