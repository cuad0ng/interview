# Auth Service

A small Spring Boot service using PostgreSQL and Spring Security HTTP Basic authentication. Registration stores BCrypt hashes (cost 12); passwords and hashes never appear in API responses. Usernames are case-sensitive and must contain 3–50 ASCII letters, digits, underscores, or hyphens. Passwords require 12–72 characters and at most 72 UTF-8 bytes.

## Run locally

Requires Java 17+, Maven 3.9+, and Docker Compose. From this directory:

```sh
docker compose up -d postgres
COOKIE_SECURE=false mvn spring-boot:run
```

The service runs on port 8081; PostgreSQL uses port 5433 to coexist with order-service. Override `PORT`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as needed. The Compose credentials are for local development. Hibernate updates the demo schema automatically.

## Register and authenticate

CSRF protection stays enabled, including on registration. Fetch a token and retain its session cookie before posting (Python 3 extracts the JSON token):

```sh
curl -s -c cookies.txt http://localhost:8081/auth/csrf > csrf.json
TOKEN=$(python3 -c 'import json; print(json.load(open("csrf.json"))["token"])')
curl -i -b cookies.txt http://localhost:8081/auth/register \
  -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct-password"}'
curl -i -u alice:correct-password http://localhost:8081/auth/me
```

`POST /auth/register` returns `201` with `{"id":1,"username":"alice"}`; invalid input returns `400`, duplicate usernames `409`, and missing/invalid CSRF tokens `403`. `GET /auth/me` returns the authenticated user's ID and username; missing or incorrect credentials return `401`. `GET /auth/csrf` returns the CSRF header name and token. There is no separate login endpoint: send HTTP Basic credentials on protected requests.

## Tests

```sh
mvn test
```

Tests use H2 without Docker and cover registration, hashing, authentication failures, duplicate usernames, validation, and CSRF enforcement.

## Demo limits

Use HTTPS outside localhost: HTTP Basic transmits credentials on each request. Cookies default to Secure, HttpOnly, and SameSite=Strict; disable Secure only for local HTTP as above. CSRF sessions expire after 15 minutes and are local to each instance. This demo has no login/registration rate limiter: add gateway throttling before exposing it publicly. Use database migrations and managed credentials before production. No JWT, OAuth, password reset, roles API, or order-service integration is included.
