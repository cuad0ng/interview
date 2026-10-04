package com.example.safeupload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipException;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class UploadSanitizer {
    private static final long MAX_IMAGE_PIXELS = 5_000_000;
    // ponytail: ZIP rewriting bounds one layer; recurse if nested archive support is required.
    private static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 1000;
    private static final int MAX_RELATIONSHIP_BYTES = 1024 * 1024;

    private UploadSanitizer() {}

    static String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "text/html", "application/xhtml+xml" -> ".html";
            case "image/svg+xml" -> ".png";
            case "application/pdf" -> ".pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            case "application/zip", "application/x-zip-compressed" -> ".zip";
            default -> throw invalid("Định dạng file không được hỗ trợ");
        };
    }

    static void sanitize(MultipartFile file, String mimeType, Path output) throws IOException {
        switch (mimeType) {
            case "image/png" -> normalizeImage(file, "png", output);
            case "image/jpeg" -> normalizeImage(file, "jpeg", output);
            case "text/html", "application/xhtml+xml" -> sanitizeHtml(file, output);
            case "image/svg+xml" -> rasterizeSvg(file, output);
            // ponytail: retain PDF after malware scanning; add CDR before making PDFs viewable.
            case "application/pdf" -> copy(file, output);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    sanitizeArchive(file, output, "word/document.xml");
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    sanitizeArchive(file, output, "xl/workbook.xml");
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    sanitizeArchive(file, output, "ppt/presentation.xml");
            case "application/zip", "application/x-zip-compressed" -> sanitizeArchive(file, output, null);
            default -> throw invalid("Định dạng file không được hỗ trợ");
        }
    }

    private static void normalizeImage(MultipartFile file, String format, Path output) throws IOException {
        try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw invalid("Ảnh không hợp lệ");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalid("Ảnh không hợp lệ");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw invalid("Ảnh vượt giới hạn kích thước");
                }
                BufferedImage image = reader.read(0);
                try (var outputStream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                    if (image == null || !ImageIO.write(image, format, outputStream)) {
                        throw invalid("Ảnh không hợp lệ");
                    }
                }
            } catch (IIOException e) {
                throw invalid("Ảnh không hợp lệ");
            } finally {
                reader.dispose();
            }
        }
    }

    private static void sanitizeHtml(MultipartFile file, Path output) throws IOException {
        String source = new String(file.getBytes(), StandardCharsets.UTF_8);
        String clean = Jsoup.clean(source, Safelist.basic());
        Files.writeString(output, clean, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static void rasterizeSvg(MultipartFile file, Path output) throws IOException {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, Boolean.FALSE);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.FALSE);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_WIDTH, 2048f);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_HEIGHT, 2048f);
        try (InputStream input = file.getInputStream(); var outputStream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(outputStream));
        } catch (TranscoderException e) {
            throw invalid("SVG không hợp lệ");
        }
    }

    private static void copy(MultipartFile file, Path output) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, output);
        }
    }

    private static void sanitizeArchive(MultipartFile file, Path output, String requiredPart) throws IOException {
        Set<String> names = new HashSet<>();
        long[] expandedBytes = {0};
        int entryCount = 0;
        boolean foundRequiredPart = requiredPart == null;
        try (ZipInputStream input = new ZipInputStream(file.getInputStream());
             ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw invalid("Archive có quá nhiều entry");
                }
                String name = safeEntryName(entry.getName());
                String key = name.toLowerCase(Locale.ROOT);
                if (!names.add(key)) {
                    throw invalid("Archive có entry trùng tên");
                }
                rejectActiveOfficePart(key);
                if (key.equals(requiredPart)) {
                    foundRequiredPart = true;
                }
                ZipEntry cleanEntry = new ZipEntry(name);
                archive.putNextEntry(cleanEntry);
                if (!entry.isDirectory()) {
                    byte[] content = name.toLowerCase(Locale.ROOT).endsWith(".rels")
                            ? readRelationship(input, expandedBytes)
                            : null;
                    if (content == null) {
                        copyBounded(input, archive, expandedBytes, MAX_ARCHIVE_BYTES);
                    } else {
                        archive.write(content);
                    }
                }
                archive.closeEntry();
                input.closeEntry();
            }
        } catch (ZipException e) {
            throw invalid("Archive không hợp lệ");
        }
        if (entryCount == 0 || !foundRequiredPart) {
            throw invalid("Archive hoặc Office document không hợp lệ");
        }
    }

    private static String safeEntryName(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[a-zA-Z]:.*") || normalized.indexOf('\0') >= 0) {
            throw invalid("Archive có đường dẫn không an toàn");
        }
        for (String part : normalized.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                throw invalid("Archive có đường dẫn không an toàn");
            }
        }
        return normalized;
    }

    private static void rejectActiveOfficePart(String name) {
        if (name.endsWith("vbaproject.bin") || name.contains("/activex/") || name.contains("/embeddings/")) {
            throw invalid("Office document chứa macro hoặc nội dung nhúng không được hỗ trợ");
        }
    }

    private static byte[] readRelationship(InputStream input, long[] expandedBytes) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        copyBounded(input, content, expandedBytes, MAX_RELATIONSHIP_BYTES);
        byte[] bytes = content.toByteArray();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        String mode = reader.getAttributeValue(null, "TargetMode");
                        if ("external".equalsIgnoreCase(mode)) {
                            throw invalid("Office document có tham chiếu ngoài");
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw invalid("Office relationship không hợp lệ");
        }
        return bytes;
    }

    private static void copyBounded(InputStream input, java.io.OutputStream output, long[] total, long limit)
            throws IOException {
        byte[] buffer = new byte[8192];
        long entryStart = total[0];
        int count;
        while ((count = input.read(buffer)) != -1) {
            total[0] += count;
            if (total[0] > MAX_ARCHIVE_BYTES || total[0] - entryStart > limit) {
                throw invalid("Archive vượt giới hạn giải nén");
            }
            output.write(buffer, 0, count);
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
