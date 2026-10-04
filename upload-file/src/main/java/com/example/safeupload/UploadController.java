package com.example.safeupload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UploadController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadController.class);
    private static final Detector DETECTOR = new DefaultDetector();
    private static final long MAX_SCANNABLE_BYTES = 24L * 1024 * 1024;

    private final Path uploadDir;
    private final FileScanner scanner;
    private final Map<String, LargeUploadSession> largeUploads = new ConcurrentHashMap<>();

    public UploadController(@Value("${app.upload-dir}") String uploadDir,
                            @Value("${app.clamdscan-command:clamdscan}") String clamdscanCommand) {
        this(uploadDir, file -> scanWithProcess(clamdscanCommand, file));
    }

    UploadController(String uploadDir, FileScanner scanner) {
        this.uploadDir = Path.of(uploadDir);
        this.scanner = scanner;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        return processUpload(file);
    }

    @PostMapping("/uploads/large")
    public LargeUploadCreated createLargeUpload(@RequestBody CreateLargeUploadRequest request) throws IOException {
        if (request.filename() == null || request.filename().isBlank()
                || request.size() <= 0 || request.parts() <= 0 || request.parts() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Large upload request khong hop le");
        }
        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = uploadDir.resolve(".large-upload").resolve(uploadId);
        Files.createDirectories(sessionDir.resolve("parts"));
        largeUploads.put(uploadId, new LargeUploadSession(request.filename(), request.size(), request.parts(),
                sessionDir, "UPLOADING", null));
        return new LargeUploadCreated(uploadId, request.parts());
    }

    @PostMapping("/uploads/{uploadId}/parts/{partNumber}")
    public LargeUploadStatus uploadLargePart(@PathVariable String uploadId, @PathVariable int partNumber,
                                             @RequestParam("file") MultipartFile file) throws IOException {
        LargeUploadSession session = session(uploadId);
        if (partNumber < 1 || partNumber > session.expectedParts) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part khong hop le");
        }
        Files.copy(file.getInputStream(), session.partsDir().resolve(Integer.toString(partNumber)),
                StandardCopyOption.REPLACE_EXISTING);
        session.status = "UPLOADING";
        return status(session);
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public LargeUploadCompleted completeLargeUpload(@PathVariable String uploadId) throws IOException {
        LargeUploadSession session = session(uploadId);
        Path assembled = session.sessionDir.resolve("assembled");
        try {
            assemble(session, assembled);
            String storedName = processUpload(new PathMultipartFile("file", session.filename, assembled));
            session.status = "DONE";
            session.storedName = storedName;
            return new LargeUploadCompleted(storedName);
        } catch (ResponseStatusException e) {
            if (!HttpStatus.CONFLICT.equals(e.getStatusCode())) {
                session.status = "FAILED";
            }
            throw e;
        } finally {
            if (!"UPLOADING".equals(session.status)) {
                deleteRecursively(session.sessionDir);
            }
        }
    }

    @GetMapping("/uploads/{uploadId}/status")
    public LargeUploadStatus largeUploadStatus(@PathVariable String uploadId) throws IOException {
        return status(session(uploadId));
    }

    private String processUpload(MultipartFile file) throws IOException {
        MediaType detectedType;
        try (var input = TikaInputStream.get(file.getInputStream())) {
            detectedType = DETECTOR.detect(input, new Metadata(), new ParseContext());
        }
        String mimeType = detectedType.toString();
        String extension = UploadSanitizer.extensionFor(mimeType);
        Path quarantineDir = uploadDir.resolve(".quarantine");
        Path quarantinedFile = quarantineDir.resolve(UUID.randomUUID() + extension);
        Files.createDirectories(quarantineDir);
        try {
            UploadSanitizer.sanitize(file, mimeType, quarantinedFile);
            if (Files.size(quarantinedFile) > MAX_SCANNABLE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File sau xu ly vuot gioi han quet");
            }
            int scanResult = scanner.scan(quarantinedFile);
            if (scanResult == 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File bi phat hien co malware");
            }
            if (scanResult != 0) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Khong the quet file");
            }
            String storedName = UUID.randomUUID() + extension;
            Files.move(quarantinedFile, uploadDir.resolve(storedName), StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Upload accepted: storedName={}, mimeType={}", storedName, mimeType);
            return storedName;
        } finally {
            Files.deleteIfExists(quarantinedFile);
        }
    }

    private LargeUploadSession session(String uploadId) {
        LargeUploadSession session = largeUploads.get(uploadId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session khong ton tai");
        }
        return session;
    }

    private LargeUploadStatus status(LargeUploadSession session) throws IOException {
        int uploadedParts = 0;
        Path partsDir = session.partsDir();
        if (Files.isDirectory(partsDir)) {
            try (DirectoryStream<Path> parts = Files.newDirectoryStream(partsDir)) {
                for (Path part : parts) {
                    if (Files.isRegularFile(part)) {
                        uploadedParts++;
                    }
                }
            }
        }
        return new LargeUploadStatus(session.status, uploadedParts, session.expectedParts, session.storedName);
    }

    private void assemble(LargeUploadSession session, Path output) throws IOException {
        long total = 0;
        try (var assembled = Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int part = 1; part <= session.expectedParts; part++) {
                Path partFile = session.partsDir().resolve(Integer.toString(part));
                if (!Files.exists(partFile)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload chua du part");
                }
                total += Files.size(partFile);
                Files.copy(partFile, assembled);
            }
        }
        if (total != session.size) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kich thuoc upload khong khop");
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int scanWithProcess(String clamdscanCommand, Path file) {
        Process scanner;
        try {
            scanner = new ProcessBuilder(clamdscanCommand, "--stream", "--no-summary", file.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Virus scanner unavailable");
        }
        try {
            if (!scanner.waitFor(30, TimeUnit.SECONDS)) {
                scanner.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Virus scanner timed out");
            }
            return scanner.exitValue();
        } catch (InterruptedException e) {
            scanner.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Virus scan interrupted");
        }
    }

    @FunctionalInterface
    interface FileScanner {
        int scan(Path file);
    }

    public record CreateLargeUploadRequest(String filename, long size, int parts) {}

    public record LargeUploadCreated(String uploadId, int expectedParts) {}

    public record LargeUploadStatus(String status, int uploadedParts, int expectedParts, String storedName) {}

    public record LargeUploadCompleted(String storedName) {}

    private static final class LargeUploadSession {
        private final String filename;
        private final long size;
        private final int expectedParts;
        private final Path sessionDir;
        private volatile String status;
        private volatile String storedName;

        private LargeUploadSession(String filename, long size, int expectedParts, Path sessionDir,
                                   String status, String storedName) {
            this.filename = filename;
            this.size = size;
            this.expectedParts = expectedParts;
            this.sessionDir = sessionDir;
            this.status = status;
            this.storedName = storedName;
        }

        private Path partsDir() {
            return sessionDir.resolve("parts");
        }
    }

    private record PathMultipartFile(String name, String originalFilename, Path path) implements MultipartFile {
        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public @NonNull byte[] getBytes() throws IOException {
            return Objects.requireNonNull(Files.readAllBytes(path));
        }

        @Override
        public @NonNull InputStream getInputStream() throws IOException {
            return Objects.requireNonNull(Files.newInputStream(path));
        }

        @Override
        public void transferTo(@NonNull java.io.File dest) throws IOException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public @NonNull String getName() {
            return Objects.requireNonNull(name);
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }
    }
}
