package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Set;

@Service
public class PrivateImageStorage {
    // Preserve retrieval of legacy uploads up to 20 MiB; new uploads are limited to 5 MiB.
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final AppProps props;

    public PrivateImageStorage(AppProps props) {
        this.props = props;
    }

    public record Image(byte[] bytes, MediaType contentType) {}

    public Image read(String relative) {
        Path path = Path.of(relative);
        if (path.isAbsolute() || relative.contains("\\") || path.getNameCount() == 0) throw notFound();
        for (Path segment : path) {
            if (!segment.toString().matches("[A-Za-z0-9_.-]+")
                    || segment.toString().equals(".") || segment.toString().equals("..")) throw notFound();
        }
        Path root = Path.of(props.getUploadsDir()).toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(root)) throw notFound();
            // The configured root is trusted; every child is opened relative to a pinned directory.
            // Fail closed on providers lacking secure directory-relative, no-follow operations.
            try (DirectoryStream<Path> directory = Files.newDirectoryStream(root.toRealPath())) {
                if (!(directory instanceof SecureDirectoryStream<Path> secure)) throw notFound();
                byte[] bytes = readWithin(secure, path, 0);
                return new Image(bytes, verifiedType(bytes));
            }
        } catch (IOException | RuntimeException exception) {
            // Image readers can also reject malformed data with unchecked exceptions.
            throw notFound();
        }
    }

    private byte[] readWithin(SecureDirectoryStream<Path> directory, Path path, int index) throws IOException {
        Path name = path.getName(index);
        if (index < path.getNameCount() - 1) {
            try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                return readWithin(child, path, index + 1);
            }
        }
        var attributes = directory.getFileAttributeView(name, BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS).readAttributes();
        if (!attributes.isRegularFile() || attributes.size() <= 0 || attributes.size() > MAX_BYTES) throw notFound();
        try (var channel = directory.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             var input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw notFound();
            return bytes;
        }
    }

    private MediaType verifiedType(byte[] bytes) {
        return ImageValidator.inspect(bytes).contentType();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
