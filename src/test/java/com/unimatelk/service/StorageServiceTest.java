package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import com.unimatelk.support.UploadTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceTest {
    @TempDir Path root;
    private StorageService storage;
    private AppProps props;
    private UploadTestSupport.Transactions transactions;
    private TransactionTemplate transaction;
    private MockMultipartFile image;

    @BeforeEach
    void setup() throws Exception {
        props = mock(AppProps.class);
        when(props.getUploadsDir()).thenReturn(root.toString());
        storage = spy(new StorageService(props));
        transactions = new UploadTestSupport.Transactions();
        transaction = new TransactionTemplate(transactions);
        image = new MockMultipartFile("file", "../../outside.php", "text/html", UploadTestSupport.image("png"));
    }

    @Test
    void unsafeOriginalFilenameCannotAffectGeneratedPathAndStreamsClose() throws Exception {
        String url = transaction.execute(status -> storage.storeChatImage(1L, 11L, image));
        assertTrue(url.matches("/uploads/chat/1/u11-[a-f0-9-]{36}\\.png"));
        assertArrayEquals(image.getBytes(), Files.readAllBytes(path(url)));
        assertEquals(1, files());
    }

    @Test
    void twoUploadsHaveDifferentNamesAndCannotOverwriteOneAnother() throws Exception {
        String first = transaction.execute(status -> storage.storeChatImage(1L, 11L, image));
        String second = transaction.execute(status -> storage.storeChatImage(1L, 11L, image));
        assertNotEquals(first, second);
        assertEquals(2, files());
        assertArrayEquals(Files.readAllBytes(path(first)), Files.readAllBytes(path(second)));
    }

    @Test
    void newFilesAndDirectoriesHaveOwnerOnlyPermissions() throws Exception {
        String url = transaction.execute(status -> storage.storeChatImage(1L, 11L, image));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path(url)));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(path(url).getParent()));
    }

    @Test
    void forcedNameCollisionPreservesTheExistingFile() throws Exception {
        doReturn("00000000-0000-4000-8000-000000000001").when(storage).randomId();
        String first = transaction.execute(status -> storage.storeChatImage(1L, 11L, image));
        byte[] original = Files.readAllBytes(path(first));
        assertThrows(ResponseStatusException.class,
                () -> transaction.execute(status -> storage.storeChatImage(1L, 11L, image)));
        assertArrayEquals(original, Files.readAllBytes(path(first)));
        assertEquals(1, files());
    }

    @Test
    void validationFailureCreatesNoDirectories() throws Exception {
        assertThrows(ResponseStatusException.class, () -> transaction.execute(status ->
                storage.storeChatImage(1L, 11L, new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1}))));
        assertFalse(Files.exists(root.resolve("chat")));
    }

    @Test
    void traversalInIdsOrProfileKindIsRejectedBeforeStorage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> transaction.execute(status -> storage.storeChatImage(-1L, 11L, image)));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.execute(status -> storage.storeProfileImage(11L, image, "../escape")));
        assertEquals(0, files());
    }

    @ParameterizedTest
    @ValueSource(strings = {"chat", "chat/1", "profiles", "profiles/11"})
    void directorySymlinksCannotEscapeStorage(String relative, @TempDir Path outside) throws Exception {
        Path link = root.resolve(relative);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outside);
        assertThrows(ResponseStatusException.class, () -> transaction.execute(status ->
                relative.startsWith("chat") ? storage.storeChatImage(1L, 11L, image)
                        : storage.storeProfileImage(11L, image, "profile")));
        try (var paths = Files.list(outside)) { assertEquals(0, paths.count()); }
    }

    @Test
    void configuredRootSymlinkIsRejected(@TempDir Path outside) throws Exception {
        Path link = outside.resolve("root-link");
        Files.createSymbolicLink(link, root);
        when(props.getUploadsDir()).thenReturn(link.toString());
        assertThrows(ResponseStatusException.class,
                () -> transaction.execute(status -> storage.storeChatImage(1L, 11L, image)));
        assertEquals(0, files());
    }

    @Test
    void partialWriteFailureRemovesOnlyTheNewFile() throws Exception {
        Path sentinel = root.resolve("synthetic-existing.txt");
        Files.writeString(sentinel, "keep");
        doAnswer(invocation -> {
            SeekableByteChannel channel = invocation.getArgument(0);
            channel.write(ByteBuffer.wrap(new byte[]{1, 2, 3}));
            throw new IOException("Synthetic partial write failure");
        }).when(storage).writeBytes(any(), any());
        assertThrows(ResponseStatusException.class,
                () -> transaction.execute(status -> storage.storeChatImage(1L, 11L, image)));
        assertEquals("keep", Files.readString(sentinel));
        assertEquals(1, files());
    }

    @Test
    void responsePreparationFailureRollsBackNewFile() throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            storage.storeChatImage(1L, 11L, image);
            throw new IllegalStateException("Synthetic preparation failure");
        }));
        assertTrue(transactions.rolledBack);
        assertEquals(0, files());
    }

    @Test
    void rollbackUsesPinnedDirectoryEvenAfterParentPathIsReplacedBySymlink(@TempDir Path outside) throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            String url = storage.storeChatImage(1L, 11L, image);
            try {
                Files.writeString(outside.resolve(path(url).getFileName()), "keep outside");
                Files.move(root.resolve("chat/1"), root.resolve("chat/moved"));
                Files.createSymbolicLink(root.resolve("chat/1"), outside);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            throw new IllegalStateException("Synthetic rollback after directory replacement");
        }));
        try (var paths = Files.list(root.resolve("chat/moved"))) { assertEquals(0, paths.count()); }
        try (var paths = Files.list(outside)) {
            assertEquals("keep outside", Files.readString(paths.findFirst().orElseThrow()));
        }
    }

    @Test
    void rollbackOnlyCompletionRemovesNewFile() throws Exception {
        transaction.execute(status -> {
            storage.storeChatImage(1L, 11L, image);
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, files());
    }

    @Test
    void storageCannotBeUsedWithoutTransactionCleanup() throws Exception {
        assertThrows(IllegalStateException.class, () -> storage.storeChatImage(1L, 11L, image));
        assertEquals(0, files());
    }

    private Path path(String url) { return root.resolve(url.substring("/uploads/".length())); }

    private long files() throws IOException {
        try (var paths = Files.walk(root)) { return paths.filter(Files::isRegularFile).count(); }
    }
}
