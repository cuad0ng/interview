# Interview

Maven multi-module project containing two independently runnable Spring Boot apps:

- [welcome-email](welcome-email/README.md): user registration and asynchronous welcome emails.
- [upload-file](upload-file/README.md): validated, sanitized, malware-scanned uploads.

The root POM aggregates both modules. Each module retains its own Spring Boot
parent and dependency versions: welcome-email uses Java 17 and Spring Boot
3.5.16; upload-file uses Java 25 and Spring Boot 3.4.0. Use JDK 25 and Maven
to build the entire project.

From this directory:

```sh
mvn test
mvn package
```

Build or run an individual module:

```sh
mvn -pl welcome-email test
mvn -pl upload-file test
mvn -pl welcome-email spring-boot:run
mvn -pl upload-file spring-boot:run
```

Both apps default to port 8080; run them separately or configure different ports.
See each module's README for required services and environment variables.

The welcome-email Docker build remains self-contained:

```sh
docker compose -f welcome-email/compose.yaml up -d --build
```

Run its Docker-backed integration tests with:

```sh
mvn -pl welcome-email -Pintegration verify
```
