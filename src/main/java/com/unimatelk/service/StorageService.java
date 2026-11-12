package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private final AppProps props;

    public StorageService(AppProps props) {
        this.props = props;
    }

    public String storeProfileImage(Long userId, MultipartFile file, String kind) {
        requireId(userId);
        if (!"profile".equals(kind) && !"cover".equals(kind)) throw new IllegalArgumentException("Invalid image kind");
        return store("profiles/" + userId, kind + "-", file);
    }

    public String storeLegacyProfileImage(Long userId, MultipartFile file) {
        requireId(userId);
        return store("", "profile_" + userId + "_", file);
    }

    public String storeChatImage(Long roomId, Long userId, MultipartFile file) {
        requireId(roomId);
        requireId(userId);
        return store("chat/" + roomId, "u" + userId + "-", file);
    }

    private String store(String relativeDirectory, String prefix, MultipartFile file) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Image storage requires a transaction");
        }
        ImageValidator.ValidatedImage image = ImageValidator.upload(file);
        String filename = prefix + randomId() + image.format().extension();
        SecureDirectoryStream<Path> directory = null;
        PendingUpload pending = null;
        boolean registered = false;
        try {
            directory = openDirectory(relativeDirectory);
            Path name = Path.of(filename);
            // CREATE_NEW also refuses an existing symlink; no upload can replace another file.
            try (var channel = directory.newByteChannel(name,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                try {
                    pending = new PendingUpload(directory, name);
                } catch (IOException identityFailure) {
                    // CREATE_NEW succeeded, but cleanup ownership could not be established.
                    try {
                        directory.deleteFile(name);
                    } catch (IOException cleanupFailure) {
                        log.error("Could not remove a newly created upload; reconciliation may be required");
                    }
                    throw identityFailure;
                }
                TransactionSynchronizationManager.registerSynchronization(pending);
                registered = true;
                writeBytes(channel, image.bytes());
            }
            return "/uploads/" + (relativeDirectory.isEmpty() ? "" : relativeDirectory + "/") + filename;
        } catch (IOException | RuntimeException exception) {
            if (pending != null) pending.removeOwnedFile();
            if (!registered && directory != null) close(directory);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage failed");
        }
    }

    // Package-visible seams allow deterministic collision and partial-write fault tests.
    String randomId() { return UUID.randomUUID().toString(); }

    void writeBytes(SeekableByteChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    private SecureDirectoryStream<Path> openDirectory(String relative) throws IOException {
        Path root = Path.of(props.getUploadsDir()).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) throw new IOException("Unsafe storage root");
        Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path absolute = root.toRealPath();
        DirectoryStream<Path> opened = Files.newDirectoryStream(absolute);
        if (!(opened instanceof SecureDirectoryStream<Path> directory)) {
            opened.close();
            throw new IOException("Secure directory operations are required");
        }
        try {
            if (!relative.isEmpty()) {
                for (String part : relative.split("/")) {
                    Path name = Path.of(part);
                    try {
                        var attributes = directory.getFileAttributeView(name, BasicFileAttributeView.class,
                                LinkOption.NOFOLLOW_LINKS).readAttributes();
                        if (!attributes.isDirectory() || attributes.isSymbolicLink()) throw new IOException("Unsafe directory");
                    } catch (NoSuchFileException missing) {
                        // Directory names are generated from fixed literals and positive database IDs.
                        // The configured root and directory namespace must be application-owned.
                        if (!absolute.toRealPath().equals(absolute)) throw new IOException("Storage directory changed");
                        try {
                            Files.createDirectory(absolute.resolve(name),
                                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                        } catch (FileAlreadyExistsException concurrentUpload) {
                            // Open with NOFOLLOW below, including if a concurrent operation created it.
                        }
                    }
                    SecureDirectoryStream<Path> child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
                    close(directory);
                    directory = child;
                    absolute = absolute.resolve(name);
                }
            }
            return directory;
        } catch (IOException | RuntimeException exception) {
            close(directory);
            throw exception;
        }
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid image owner or room");
    }

    private static void close(DirectoryStream<Path> directory) {
        try {
            directory.close();
        } catch (IOException exception) {
            log.error("Could not close an upload directory handle");
        }
    }

    private static final class PendingUpload implements TransactionSynchronization {
        private final SecureDirectoryStream<Path> directory;
        private final Path name;
        private final Object fileKey;

        PendingUpload(SecureDirectoryStream<Path> directory, Path name) throws IOException {
            this.directory = directory;
            this.name = name;
            this.fileKey = directory.getFileAttributeView(name, BasicFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS).readAttributes().fileKey();
            if (fileKey == null) throw new IOException("Stable file identity is required");
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == STATUS_ROLLED_BACK) removeOwnedFile();
                else if (status == STATUS_UNKNOWN) {
                    // A commit may have succeeded: deleting here could break a persisted message.
                    log.error("Upload transaction outcome is unknown; reconciliation may be required");
                }
            } finally {
                close(directory);
            }
        }

        void removeOwnedFile() {
            try {
                var attributes = directory.getFileAttributeView(name, BasicFileAttributeView.class,
                        LinkOption.NOFOLLOW_LINKS).readAttributes();
                if (fileKey != null && Objects.equals(fileKey, attributes.fileKey())) directory.deleteFile(name);
                else log.error("Upload identity changed; refusing to remove a replacement file");
            } catch (NoSuchFileException alreadyRemoved) {
                // A write failure may already have removed this newly created file.
            } catch (IOException exception) {
                log.error("Could not remove a failed upload; reconciliation may be required");
            }
        }
    }
}
