package com.unimatelk.api;

import com.jayway.jsonpath.JsonPath;
import com.unimatelk.config.AppProps;
import com.unimatelk.config.SecurityConfig;
import com.unimatelk.domain.*;
import com.unimatelk.repo.*;
import com.unimatelk.service.*;
import com.unimatelk.support.UploadTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ChatRestController.class, ProfileController.class, UploadedMediaController.class})
@Import({SecurityConfig.class, CurrentUserService.class, ChatService.class, ChatImageUploadService.class,
        StorageService.class, ProfileService.class, UploadedMediaService.class, PrivateImageStorage.class,
        ImageUploadSecurityTest.Configuration.class})
class ImageUploadSecurityTest {
    private static final String CHAT_POST = "/api/chat/rooms/1/image";
    private static final String EMAIL = "upload-viewer@example.invalid";

    @Autowired MockMvc mvc;
    @Autowired UploadTestSupport.Transactions transactions;
    @MockitoBean AppProps props;
    @MockitoBean AppUserRepository users;
    @MockitoBean ChatRoomRepository rooms;
    @MockitoBean ChatMemberRepository members;
    @MockitoBean MessageRepository messages;
    @MockitoBean BlockRepository blocks;
    @MockitoBean ProfileRepository profiles;
    @MockitoBean SimpMessagingTemplate messaging;
    @MockitoBean EntityManager entityManager;
    @MockitoBean EntityManagerFactory entityManagerFactory;
    @TempDir Path root;

    private AppUser viewer;
    private ChatRoom room;
    private Profile profile;

    @TestConfiguration
    @EnableTransactionManagement
    static class Configuration {
        @Bean UploadTestSupport.Transactions transactionManager() { return new UploadTestSupport.Transactions(); }

        @Bean MyProfileController myProfileController(CurrentUserService current, StorageService storage) {
            // @PersistenceContext receives a shared proxy backed only by the mocked factory.
            return new MyProfileController(current, storage);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        transactions.committed = false;
        transactions.rolledBack = false;
        transactions.failCommit = false;
        transactions.beforeCommit = () -> {};
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManagerFactory.createEntityManager(anyMap())).thenReturn(entityManager);
        when(props.getUploadsDir()).thenReturn(root.toString());
        viewer = new AppUser();
        viewer.setId(11L);
        viewer.setEmail(EMAIL);
        viewer.setName("Synthetic uploader");
        viewer.setStatus("ACTIVE");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(viewer));
        room = new ChatRoom();
        ReflectionTestUtils.setField(room, "id", 1L);
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(members.existsByRoomAndUser(room, viewer)).thenReturn(true);
        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(true);
        ChatMember member = new ChatMember();
        member.setUser(viewer);
        AppUser other = new AppUser();
        other.setId(12L);
        ChatMember recipient = new ChatMember();
        recipient.setUser(other);
        when(members.findByRoom(room)).thenReturn(List.of(member, recipient));
        when(messages.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1L);
            return message;
        });
        profile = new Profile();
        profile.setUser(viewer);
        when(profiles.findByUser(viewer)).thenReturn(Optional.of(profile));
        when(profiles.findByUserId(11L)).thenReturn(Optional.of(profile));
        when(profiles.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
    }

    private RequestPostProcessor viewer() {
        return oauth2Login().attributes(attributes -> attributes.put("email", EMAIL));
    }

    private MockMultipartFile image(String format) throws IOException {
        return new MockMultipartFile("file", "../../unsafe.php", "application/octet-stream", UploadTestSupport.image(format));
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "webp"})
    void memberUploadsSupportedImagesAndBroadcastsOnlyAfterCommit(String format) throws Exception {
        doAnswer(invocation -> {
            assertTrue(transactions.committed);
            ChatDtos.MessageDto dto = invocation.getArgument(1);
            assertTrue(Files.isRegularFile(path(dto.attachmentUrl())));
            assertEquals("IMAGE", dto.type());
            return null;
        }).when(messaging).convertAndSend(eq("/topic/rooms/1"), any(Object.class));
        var result = mvc.perform(multipart(CHAT_POST).file(image(format)).with(viewer()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.roomId").value(1)).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.attachmentUrl");
        assertTrue(url.startsWith("/uploads/chat/1/u11-"));
        assertTrue(url.endsWith(format.equals("jpeg") ? ".jpg" : "." + format));
        verify(messaging).convertAndSend(eq("/topic/rooms/1"), any(Object.class));
        verify(users, never()).save(any());

        Message referenced = new Message();
        referenced.setType("IMAGE");
        referenced.setAttachmentUrl(url);
        when(messages.findByRoomIdAndTypeAndAttachmentUrl(1L, "IMAGE", url)).thenReturn(List.of(referenced));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk())
                .andExpect(content().contentType("image/" + format));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void nonMemberIsRejectedBeforeCreatingDirectoriesOrSavingMessages() throws Exception {
        when(members.existsByRoomAndUser(room, viewer)).thenReturn(false);
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf()))
                .andExpect(status().isForbidden());
        assertNoFilesOrBroadcast();
        assertFalse(Files.exists(root.resolve("chat")));
        verify(messages, never()).save(any());
    }

    @Test
    void missingRoomAndInvalidRoomAreRejectedBeforeStorage() throws Exception {
        when(rooms.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/chat/rooms/0/image").file(image("png")).with(viewer()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertNoFilesOrBroadcast();
    }

    @Test
    void textAndImageBlockPolicyIsConsistentBeforeStorage() throws Exception {
        when(blocks.existsEitherDirection(eq(viewer), any(AppUser.class))).thenReturn(true);
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        assertNoFilesOrBroadcast();
        assertFalse(Files.exists(root.resolve("chat")));
        verify(messages, never()).save(any());
    }

    @Test
    void anonymousUploadAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer())).andExpect(status().isForbidden());
        assertNoFilesOrBroadcast();
        verifyNoInteractions(users);
    }

    @Test
    void missingUserIsRejectedWithoutProvisioningAnAccount() throws Exception {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
        assertNoFilesOrBroadcast();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANNED", "TEMP_BLOCKED", "PENDING"})
    void inactiveUsersCannotUpload(String status) throws Exception {
        viewer.setStatus(status);
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isForbidden());
        assertNoFilesOrBroadcast();
    }

    @Test
    void emptySpoofedMalformedAndOversizedRequestsFailWithoutSideEffects() throws Exception {
        for (byte[] bytes : List.of(new byte[0], "<html>synthetic</html>".getBytes(), new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
            mvc.perform(multipart(CHAT_POST).file(new MockMultipartFile("file", "image.png", "image/png", bytes))
                            .with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart(CHAT_POST).file(new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1]))
                        .with(viewer()).with(csrf())).andExpect(status().is(413));
        assertNoFilesOrBroadcast();
        verify(messages, never()).save(any());
    }

    @Test
    void messageSaveFailureRollsBackFileWithoutBroadcast() throws Exception {
        when(messages.save(any(Message.class))).thenThrow(new DataIntegrityViolationException("Synthetic save failure"));
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        assertTrue(transactions.rolledBack);
        assertNoFilesOrBroadcast();
    }

    @Test
    void failureAfterSaveDuringCommitPreparationCleansUpFile() throws Exception {
        transactions.beforeCommit = () -> { throw new DataIntegrityViolationException("Synthetic flush failure"); };
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        verify(messages).save(any(Message.class));
        assertTrue(transactions.rolledBack);
        assertNoFilesOrBroadcast();
    }

    @Test
    void ambiguousCommitFailureRetainsFileAndDoesNotBroadcast() throws Exception {
        transactions.failCommit = true;
        assertThrows(Exception.class, () -> mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())));
        assertEquals(1, files());
        verifyNoInteractions(messaging);
    }

    @Test
    void responsePreparationFailureRollsBackBeforeBroadcast() throws Exception {
        Message invalid = mock(Message.class);
        when(invalid.getType()).thenThrow(new IllegalArgumentException("Synthetic preparation failure"));
        when(messages.save(any(Message.class))).thenReturn(invalid);
        mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        assertNoFilesOrBroadcast();
    }

    @Test
    void broadcastFailureAfterCommitNeverRemovesPersistedImage() throws Exception {
        doThrow(new IllegalStateException("Synthetic broker failure")).when(messaging)
                .convertAndSend(eq("/topic/rooms/1"), any(Object.class));
        assertThrows(Exception.class, () -> mvc.perform(multipart(CHAT_POST).file(image("png")).with(viewer()).with(csrf())));
        assertTrue(transactions.committed);
        assertEquals(1, files());
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "cover"})
    void profileAndCoverUploadsRemainRetrievable(String kind) throws Exception {
        var result = mvc.perform(multipart("/api/profile/me/" + kind + "-photo").file(image("png"))
                        .with(viewer()).with(csrf())).andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.url");
        assertTrue(url.matches("/uploads/profiles/11/" + kind + "-[a-f0-9-]{36}\\.png"));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        verifyNoInteractions(messaging);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file", "image", "photo"})
    void legacyProfilePhotoAliasesRemainCompatible(String partName) throws Exception {
        var part = new MockMultipartFile(partName, "../../unsafe.svg", "image/svg+xml", UploadTestSupport.image("webp"));
        var result = mvc.perform(multipart("/api/my/profile/photo").file(part).with(viewer()).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.url");
        assertTrue(url.matches("/uploads/profile_11_[a-f0-9-]{36}\\.webp"));
        viewer.setPictureUrl(url);
        when(users.findById(11L)).thenReturn(Optional.of(viewer));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk()).andExpect(content().contentType("image/webp"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/my/profile/photo", "/api/profile/me/profile-photo", "/api/profile/me/cover-photo"})
    void everyProfileUploadPathRejectsUnsupportedAndOversizedContent(String endpoint) throws Exception {
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1}))
                        .with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1]))
                        .with(viewer()).with(csrf())).andExpect(status().is(413));
        assertNoFilesOrBroadcast();
    }

    @Test
    void profilePersistenceFailureAlsoCleansUpNewFile() throws Exception {
        when(profiles.save(any(Profile.class))).thenThrow(new DataIntegrityViolationException("Synthetic profile save failure"));
        mvc.perform(multipart("/api/profile/me/profile-photo").file(image("png")).with(viewer()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertNoFilesOrBroadcast();
    }

    @Test
    void legacyProfilePersistenceFailureCleansUpNewFile() throws Exception {
        when(entityManager.createNativeQuery(anyString())).thenThrow(new DataIntegrityViolationException("Synthetic SQL failure"));
        mvc.perform(multipart("/api/my/profile/photo").file(image("png")).with(viewer()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertNoFilesOrBroadcast();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/my/profile/photo", "/api/profile/me/profile-photo", "/api/profile/me/cover-photo"})
    void profileCommitPreparationFailureCleansUpAfterSaveReturns(String endpoint) throws Exception {
        transactions.beforeCommit = () -> { throw new DataIntegrityViolationException("Synthetic profile flush failure"); };
        mvc.perform(multipart(endpoint).file(image("png")).with(viewer()).with(csrf())).andExpect(status().isBadRequest());
        assertTrue(transactions.rolledBack);
        assertNoFilesOrBroadcast();
    }

    private Path path(String url) { return root.resolve(url.substring("/uploads/".length())); }

    private long files() throws IOException {
        try (var paths = Files.walk(root)) { return paths.filter(Files::isRegularFile).count(); }
    }

    private void assertNoFilesOrBroadcast() throws IOException {
        assertEquals(0, files());
        verifyNoInteractions(messaging);
    }
}
