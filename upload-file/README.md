# Safe Upload Demo

Spring Boot upload demo. Apache Tika detects uploaded content; `/upload` allows PNG, JPEG, HTML, SVG, PDF, DOCX, XLSX, PPTX, and ZIP. Large files use a separate chunked flow under `/uploads/**`: create a session, upload numbered parts, complete the session, then poll status.
Spring rejects files larger than 5 MB before the controller processes them.
PNG/JPEG are decoded and re-encoded with a 5-megapixel limit. HTML is cleaned with a basic safelist. SVG is rasterized to PNG with scripts and external resources disabled. ZIP and Office packages are rewritten with path traversal, macro, external relationship, entry-count, and 20 MB expanded-size checks. PDF is malware-scanned but otherwise kept unchanged; it is stored outside the web root and this app does not serve it inline.
Accepted files are held in `.quarantine`, scanned by `clamdscan`, then moved under a generated UUID filename. Infected or unscannable files are not accepted. A running `clamd` daemon is required; set `CLAMDSCAN_COMMAND` if the executable is not on `PATH`, or `APP_UPLOAD_DIR` to change storage.
The upload endpoint requires a shared bearer token of at least 32 bytes, configured with `UPLOAD_API_TOKEN`. Requests without a valid token receive `401`; authenticated uploads are limited to 20 in any rolling 60-second window across this application process and excess requests receive `429`. The in-memory global limiter is for a single-instance demo; use a shared per-user limiter for multiple instances. Successful uploads log only the generated stored name and detected MIME type, not the client filename or file contents. Deploy behind HTTPS and rotate the token if it is exposed.

Run with Java 25 and Maven from this module directory:

```sh
mvn spring-boot:run
```

Try an upload:

```sh
export UPLOAD_API_TOKEN="replace-with-a-random-secret-at-least-32-bytes"
curl -H "Authorization: Bearer $UPLOAD_API_TOKEN" -F "file=@photo.png" http://localhost:8080/upload
```

Try a large upload:

```sh
curl -H "Authorization: Bearer $UPLOAD_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"filename":"photo.png","size":12345,"parts":3}' \
  http://localhost:8080/uploads/large

curl -H "Authorization: Bearer $UPLOAD_API_TOKEN" \
  -F "file=@part-1.bin" \
  http://localhost:8080/uploads/{uploadId}/parts/1

curl -H "Authorization: Bearer $UPLOAD_API_TOKEN" \
  -X POST \
  http://localhost:8080/uploads/{uploadId}/complete

curl -H "Authorization: Bearer $UPLOAD_API_TOKEN" \
  http://localhost:8080/uploads/{uploadId}/status
```

Generate a token with `openssl rand -hex 32` and set it in the environment before starting the application.

Run the tests:

```sh
mvn test
```
