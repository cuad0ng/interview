# Product service tasks

- [x] Bootstrap standalone service and test configuration.
- [x] Verify failing API tests, implement create/get/list and input validation, then pass tests.
- [x] Document setup, package, verify live HTTP flow, and review changes.

Evidence: `mvn verify` exited 0; 14 integration tests passed with no skips. Packaged jar started on port 8082 against PostgreSQL 16. Live HTTP assertions passed for empty list, creation, exact decimal price, retrieval, ordered list, 404 and 400.
