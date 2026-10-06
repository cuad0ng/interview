# Order Service Tasks

## Task 1: Create the Spring Boot project and local PostgreSQL setup
- [x] Build with declared Java and Spring Boot versions.
- [x] Configure PostgreSQL through environment variables.
- [x] Provide local PostgreSQL startup setup.
- [x] Verify the project build and database startup.
- Dependencies: None

## Task 2: Implement order creation end to end
- [x] Persist customer ID and one or more item lines (product ID and positive quantity).
- [x] Return `201 Created` with generated order ID and saved details.
- [x] Return `400 Bad Request` for missing/invalid request fields.
- [x] Test valid and invalid input; verify against PostgreSQL.
- Dependencies: Task 1

## Checkpoint: Core service
- [x] Build succeeds and tests pass (`mvn test`: 3 tests).
- [x] Valid orders persist and return the expected response (H2-backed endpoint integration test).
- [x] Invalid requests are rejected with `400` (endpoint integration tests).

## Task 3: Document running and calling the service
- [x] Document prerequisites, startup, database configuration, and example request/response.
- [x] Check documented commands and response shape against project configuration.
- Dependencies: Tasks 1 and 2

## Checkpoint: Complete
- [ ] All acceptance criteria met.
- [ ] Ready for review.
