package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;

@Service
public class StorageService {

    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");
    private final AppProps props;

    public StorageService(AppProps props) {
        this.props = props;
    }

    public String storeProfileImage(Long userId, MultipartFile file, String kind) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        if (!ALLOWED.contains(file.getContentType())) throw new IllegalArgumentException("Only PNG/JPG/WEBP allowed");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Max file size is 5MB");

        String ext = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> "";
        };

        Path base = Paths.get(props.getUploadsDir()).toAbsolutePath().normalize();
        Path dir = base.resolve("profiles").resolve(String.valueOf(userId));
        Files.createDirectories(dir);

        String name = kind + "-" + Instant.now().toEpochMilli() + ext;
        Path target = dir.resolve(name);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/profiles/" + userId + "/" + name;
    }

    public String storeChatImage(Long roomId, Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        if (!ALLOWED.contains(file.getContentType())) throw new IllegalArgumentException("Only PNG/JPG/WEBP allowed");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Max file size is 5MB");

        String ext = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> "";
        };

        Path base = Paths.get(props.getUploadsDir()).toAbsolutePath().normalize();
        Path dir = base.resolve("chat").resolve(String.valueOf(roomId));
        Files.createDirectories(dir);

        String name = "u" + userId + "-" + Instant.now().toEpochMilli() + ext;
        Path target = dir.resolve(name);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/chat/" + roomId + "/" + name;
    }
}
