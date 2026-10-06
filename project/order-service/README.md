# Order Service

A small Spring Boot demo with a single `POST /orders` API. It stores orders and their item lines in PostgreSQL.

## Requirements

- Java 17+
- Maven 3.9+
- Docker Compose (for the local PostgreSQL instance)

## Run locally

From this directory, start PostgreSQL:

```sh
docker compose up -d postgres
```

Then run the service:

```sh
mvn spring-boot:run
```

The defaults connect to `jdbc:postgresql://localhost:5432/orders` with user/password `orders`. Override with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as needed. Hibernate updates the demo schema automatically.

## Create an order

```sh
curl -i http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"customer-1","items":[{"productId":"product-1","quantity":2}]}'
```

A successful request returns `201 Created`, a `Location` header, and a response similar to:

```json
{"id":1,"customerId":"customer-1","items":[{"productId":"product-1","quantity":2}]}
```

Customer ID and product IDs must be non-blank. At least one item is required, and each quantity must be positive. Invalid requests return `400 Bad Request`.

## Tests

```sh
mvn test
```

The endpoint tests use an in-memory H2 database and do not require Docker.
