# Product service implementation plan

Build a standalone Spring Boot 3.5.6 / Java 17 service following order-service conventions.

Contract: POST /products accepts name (nonblank, at most 255 characters) and price (required, nonnegative, at most 10 integer digits and 2 decimal places). It returns 201, a Location header, and {id,name,price}. GET /products/{id} returns the stored product or 404; GET /products returns products ordered by ID, including an empty array when none exist. Invalid inputs return 400. Price uses BigDecimal and NUMERIC(12,2).

1. Bootstrap Maven, PostgreSQL configuration (HTTP 8082, database port 5434), and H2 test configuration. Verify test compilation.
2. Write endpoint integration tests first; prove failure, then implement entity, repository, validated request, and controller. Verify creation, persistence, reads, missing products, malformed requests, and money boundaries.
3. Document setup and API examples, package the executable jar, and verify HTTP behavior with the test database. Review all new files and complete the checklist.

Scope: only project/product-service and the implemented-service link in root README. No existing callers change. No update/delete, auth, inventory, or interservice wiring. Demo schema uses Hibernate update; production migrations and access control remain future work. H2 tests do not prove PostgreSQL-specific runtime behavior.
