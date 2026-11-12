package com.unimatelk.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** One content-based policy for new uploads and existing protected image retrieval. */
public final class ImageValidator {
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000;
    private static final int MAX_DIMENSION = 16_384;

    private ImageValidator() {}

    public enum Format {
        PNG(".png", MediaType.IMAGE_PNG), JPEG(".jpg", MediaType.IMAGE_JPEG),
        WEBP(".webp", MediaType.parseMediaType("image/webp"));

        private final String extension;
        private final MediaType contentType;

        Format(String extension, MediaType contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() { return extension; }
        public MediaType contentType() { return contentType; }
    }

    public record ValidatedImage(byte[] bytes, Format format) {}

    public static ValidatedImage upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_UPLOAD_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) {
            // Bound the stream as well as checking the claimed size.
            bytes = input.readNBytes(MAX_UPLOAD_BYTES + 1);
        } catch (IOException exception) {
            throw invalid();
        }
        if (bytes.length > MAX_UPLOAD_BYTES) throw tooLarge();
        return new ValidatedImage(bytes, inspect(bytes));
    }

    public static Format inspect(byte[] bytes) {
        if (bytes.length == 0) throw invalid();
        // Never use a file-backed ImageIO cache or a client-supplied MIME type/filename.
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                Format format = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> Format.PNG;
                    case "jpeg", "jpg" -> Format.JPEG;
                    case "webp" -> Format.WEBP;
                    default -> throw invalid();
                };
                reader.setInput(input, true, true);
                reader.addIIOReadWarningListener((source, warning) -> { throw invalid(); });
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) throw invalid();
                if (reader.read(0) == null) throw invalid();
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid PNG, JPEG or WebP image is required");
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Max image size is 5 MiB");
    }
}
