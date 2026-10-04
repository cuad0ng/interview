package com.example.safeupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;

class UploadControllerTest {
    @Test
    void rejectsUnrecognizedContentBeforeScanning(@TempDir Path uploadDir) {
        var controller = new UploadController(uploadDir.toString(), path -> 0);
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.upload(
                new MockMultipartFile("file", "photo.png", "image/png", "not image".getBytes())));
    }

    @Test
    void completesLargeUploadThroughSharedScanPipeline(@TempDir Path uploadDir) throws Exception {
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", image);
        byte[] bytes = image.toByteArray();
        var controller = new UploadController(uploadDir.toString(), path -> 0);

        var created = controller.createLargeUpload(new UploadController.CreateLargeUploadRequest(
                "photo.png", bytes.length, 2));
        int split = bytes.length / 2;
        controller.uploadLargePart(created.uploadId(), 1, new MockMultipartFile(
                "file", "part-1", "application/octet-stream", Arrays.copyOfRange(bytes, 0, split)));
        controller.uploadLargePart(created.uploadId(), 2, new MockMultipartFile(
                "file", "part-2", "application/octet-stream", Arrays.copyOfRange(bytes, split, bytes.length)));

        var completed = controller.completeLargeUpload(created.uploadId());

        assertEquals("DONE", controller.largeUploadStatus(created.uploadId()).status());
        assertTrue(Files.exists(uploadDir.resolve(completed.storedName())));
    }

    @Test
    void rejectsIncompleteLargeUpload(@TempDir Path uploadDir) throws Exception {
        var controller = new UploadController(uploadDir.toString(), path -> 0);
        var created = controller.createLargeUpload(new UploadController.CreateLargeUploadRequest(
                "photo.png", 10, 2));
        controller.uploadLargePart(created.uploadId(), 1, new MockMultipartFile(
                "file", "part-1", "application/octet-stream", "abc".getBytes()));

        var error = Assertions.assertThrows(ResponseStatusException.class,
                () -> controller.completeLargeUpload(created.uploadId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("UPLOADING", controller.largeUploadStatus(created.uploadId()).status());
    }

    @Test
    void reencodesImageAndDropsTrailingPayload(@TempDir Path uploadDir) throws Exception {
        var original = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", original);
        byte[] trailingPayload = "untrusted trailing data".getBytes(StandardCharsets.UTF_8);
        byte[] input = Arrays.copyOf(original.toByteArray(), original.size() + trailingPayload.length);
        System.arraycopy(trailingPayload, 0, input, original.size(), trailingPayload.length);
        var file = new MockMultipartFile("file", "photo.png", "image/png", input);
        Path normalized = uploadDir.resolve("normalized.png");

        UploadSanitizer.sanitize(file, "image/png", normalized);

        assertTrue(Files.size(normalized) < input.length);
        assertEquals(1, ImageIO.read(normalized.toFile()).getWidth());
    }

    @Test
    void sanitizesHtmlAndRejectsUnsafeArchives(@TempDir Path uploadDir) throws Exception {
        Path htmlOutput = uploadDir.resolve("clean.html");
        UploadSanitizer.sanitize(new MockMultipartFile("file", "page.html", "text/html",
                "<script>alert(1)</script><p onclick='alert(2)'>safe</p>".getBytes()), "text/html", htmlOutput);
        String cleanHtml = Files.readString(htmlOutput);
        assertFalse(cleanHtml.contains("<script"));
        assertFalse(cleanHtml.contains("onclick"));

        var archiveBytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(archiveBytes)) {
            zip.putNextEntry(new ZipEntry("../escape.txt"));
            zip.write("bad".getBytes());
            zip.closeEntry();
        }
        Assertions.assertThrows(ResponseStatusException.class, () -> UploadSanitizer.sanitize(
                new MockMultipartFile("file", "archive.zip", "application/zip", archiveBytes.toByteArray()),
                "application/zip", uploadDir.resolve("clean.zip")));
    }
}
