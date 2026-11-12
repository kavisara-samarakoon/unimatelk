package com.unimatelk.api;

import com.unimatelk.config.AppProps;
import com.unimatelk.config.SecurityConfig;
import com.unimatelk.config.StaticResourceConfig;
import com.unimatelk.config.WebConfig;
import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Message;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.ChatMemberRepository;
import com.unimatelk.repo.MessageRepository;
import com.unimatelk.repo.ProfileRepository;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.PrivateImageStorage;
import com.unimatelk.service.UploadedMediaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UploadedMediaController.class)
@Import({SecurityConfig.class, StaticResourceConfig.class, WebConfig.class,
        UploadedMediaService.class, PrivateImageStorage.class, CurrentUserService.class})
class UploadedMediaSecurityTest {
    private static final String NAME = "u11-1772437528169.jpg";
    private static final String CHAT_URL = "/uploads/chat/1/" + NAME;
    private static final String EMAIL = "viewer@example.invalid";

    @Autowired MockMvc mvc;
    @MockitoBean AppProps props;
    @MockitoBean AppUserRepository users;
    @MockitoBean ChatMemberRepository members;
    @MockitoBean MessageRepository messages;
    @MockitoBean ProfileRepository profiles;
    @TempDir Path storage;

    private AppUser viewer;
    private byte[] png;

    @BeforeEach
    void setup() throws Exception {
        when(props.getUploadsDir()).thenReturn(storage.toString());
        viewer = new AppUser();
        viewer.setId(11L);
        viewer.setEmail(EMAIL);
        viewer.setName("Synthetic viewer");
        viewer.setStatus("ACTIVE");
        viewer.setRole("STUDENT");
        viewer.setLastActiveAt(Instant.EPOCH);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(viewer));
        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(true);
        Message message = new Message();
        message.setType("IMAGE");
        message.setAttachmentUrl(CHAT_URL);
        when(messages.findByRoomIdAndTypeAndAttachmentUrl(1L, "IMAGE", CHAT_URL)).thenReturn(List.of(message));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        png = output.toByteArray();
        // Deliberately use a .jpg name with PNG bytes: response MIME must come from content.
        write(CHAT_URL, png);
    }

    @AfterEach
    void retrievalNeverWritesUserOrMediaRecords() {
        verify(users, never()).save(any());
        verify(profiles, never()).save(any());
        verify(messages, never()).save(any());
        verify(members, never()).save(any());
        assertEquals(Instant.EPOCH, viewer.getLastActiveAt());
        assertEquals("Synthetic viewer", viewer.getName());
    }

    private RequestPostProcessor viewer() {
        // Both local login and Google login expose an OAuth2User principal.
        return oauth2Login().attributes(attributes -> attributes.put("email", EMAIL));
    }

    private void write(String url, byte[] bytes) throws Exception {
        Path path = storage.resolve(url.substring("/uploads/".length()));
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private void profileReference(String url, boolean cover) {
        Profile profile = new Profile();
        profile.setUser(viewer);
        if (cover) profile.setCoverPhotoPath(url);
        else profile.setProfilePhotoPath(url);
        when(profiles.findByUserId(11L)).thenReturn(Optional.of(profile));
    }

    @Test
    void anonymousChatGetAndHeadAreUnauthorized() throws Exception {
        mvc.perform(get(CHAT_URL)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(head(CHAT_URL)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(users, members, messages, profiles);
    }

    @Test
    void roomMemberGetsVerifiedImageAndSecurityHeaders() throws Exception {
        mvc.perform(get(CHAT_URL).with(viewer()))
                .andExpect(status().isOk()).andExpect(content().bytes(png))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void anotherRoomMemberCanReadWithoutOwningTheImage() throws Exception {
        viewer.setId(12L);
        when(members.existsByRoomIdAndUserId(1L, 12L)).thenReturn(true);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isOk());
    }

    @Test
    void authorizedHeadHasGetMetadataWithoutBody() throws Exception {
        mvc.perform(head(CHAT_URL).with(viewer())).andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, png.length))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(""));
    }

    @Test
    void nonMemberCannotGetOrHeadEvenWhenFileAndMessageExist() throws Exception {
        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(false);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isForbidden()).andExpect(content().string(""));
        mvc.perform(head(CHAT_URL).with(viewer())).andExpect(status().isForbidden()).andExpect(content().string(""));
        verifyNoInteractions(messages);
    }

    @Test
    void missingAndMalformedIdentityNeverProvisionUsers() throws Exception {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isUnauthorized());
        mvc.perform(get(CHAT_URL).with(oauth2Login())).andExpect(status().isUnauthorized());
        mvc.perform(get(CHAT_URL).with(user("wrong-principal"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(members, messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANNED", "TEMP_BLOCKED", "PENDING", ""})
    void onlyActiveExistingUsersCanRead(String accountStatus) throws Exception {
        viewer.setStatus(accountStatus);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isForbidden());
        verifyNoInteractions(members, messages);
    }

    @Test
    void unreferencedFileIsNotFound() throws Exception {
        when(messages.findByRoomIdAndTypeAndAttachmentUrl(1L, "IMAGE", CHAT_URL)).thenReturn(List.of());
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void databaseCollationCannotRelaxExactUrlOrImageType() throws Exception {
        Message message = new Message();
        message.setType("IMAGE");
        message.setAttachmentUrl(CHAT_URL.toUpperCase());
        when(messages.findByRoomIdAndTypeAndAttachmentUrl(1L, "IMAGE", CHAT_URL)).thenReturn(List.of(message));
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
        message.setAttachmentUrl(CHAT_URL);
        message.setType("image");
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void missingReferencedFileIsNotFound() throws Exception {
        Files.delete(storage.resolve("chat/1/" + NAME));
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void aDifferentRoomDoesNotReuseMessageAuthorization() throws Exception {
        when(members.existsByRoomIdAndUserId(2L, 11L)).thenReturn(true);
        write(CHAT_URL.replace("/1/", "/2/"), png);
        mvc.perform(get(CHAT_URL.replace("/1/", "/2/")).with(viewer())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "01", "not-a-room", "9223372036854775808"})
    void invalidRoomIdsAreRejected(String roomId) throws Exception {
        mvc.perform(get("/uploads/chat/" + roomId + "/" + NAME).with(viewer())).andExpect(status().isNotFound());
        verifyNoInteractions(members, messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpected.png", "u11-1772437528169.svg", "u11-1772437528169.jpg.bak", "u11-1.html"})
    void unexpectedChatFilenamesAreRejected(String filename) throws Exception {
        mvc.perform(get("/uploads/chat/1/" + filename).with(viewer())).andExpect(status().isNotFound());
        verifyNoInteractions(members, messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/uploads/chat/1/../u11-1.png", "/uploads/chat/1/%2e%2e%2foutside.png",
            "/uploads/chat/1/%252e%252e%252foutside.png", "/uploads/chat/1/..%5coutside.png",
            "/uploads/chat/1//u11-1.png", "/uploads/profiles/11/../../chat/1/u11-1.png"})
    void traversalIsRejectedByFirewallOrController(String path) throws Exception {
        mvc.perform(get(URI.create(path)).with(viewer())).andExpect(status().is4xxClientError());
    }

    @Test
    void fileSymlinkEscapeIsRejected(@TempDir Path outside) throws Exception {
        Path target = outside.resolve("synthetic.png");
        Files.write(target, png);
        Path link = storage.resolve("chat/1/" + NAME);
        Files.delete(link);
        Files.createSymbolicLink(link, target);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void directorySymlinkEscapeIsRejected(@TempDir Path outside) throws Exception {
        Files.write(outside.resolve(NAME), png);
        Files.delete(storage.resolve("chat/1/" + NAME));
        Files.delete(storage.resolve("chat/1"));
        Files.createSymbolicLink(storage.resolve("chat/1"), outside);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void configuredStorageRootSymlinkIsRejected(@TempDir Path outside) throws Exception {
        Path link = outside.resolve("root-link");
        Files.createSymbolicLink(link, storage);
        when(props.getUploadsDir()).thenReturn(link.toString());
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedConditionalAndRangeRequestsCannotBypassAuthorization() throws Exception {
        for (var request : List.of(get(CHAT_URL), head(CHAT_URL))) {
            mvc.perform(request.header(HttpHeaders.IF_NONE_MATCH, "*")
                            .header(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 01 Jan 2031 00:00:00 GMT")
                            .header(HttpHeaders.RANGE, "bytes=0-5"))
                    .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        }
        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(false);
        for (var request : List.of(get(CHAT_URL), head(CHAT_URL))) {
            mvc.perform(request.with(viewer()).header(HttpHeaders.IF_NONE_MATCH, "*")
                            .header(HttpHeaders.RANGE, "bytes=0-5").header(HttpHeaders.IF_RANGE, "\"old\""))
                    .andExpect(status().isForbidden()).andExpect(content().string(""))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        }
    }

    @Test
    void authorizedConditionalAndRangeRequestsStillAuthorizeAndReturnFullImage() throws Exception {
        mvc.perform(get(CHAT_URL).with(viewer()).header(HttpHeaders.IF_NONE_MATCH, "*")
                        .header(HttpHeaders.RANGE, "bytes=0-5"))
                .andExpect(status().isOk()).andExpect(content().bytes(png))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE));
        verify(members).existsByRoomIdAndUserId(1L, 11L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/uploads/profiles/11/profile-1772437528169.png",
            "/uploads/profiles/11/cover-1772437528169.webp",
            "/uploads/profile-11-1772437528169.JPG",
            "/uploads/profile_11_a2ce6ead-3be6-42bb-a102-1f8340b05885.jpeg"})
    void authenticatedViewersCanReadEveryReferencedProfilePattern(String url) throws Exception {
        write(url, png);
        profileReference(url, url.contains("cover-"));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk()).andExpect(content().bytes(png))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(head(url).with(viewer())).andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void userPictureReferenceAlonePreservesAvatarCompatibility() throws Exception {
        String url = "/uploads/profile-11-1772437528169.PNG";
        write(url, png);
        viewer.setPictureUrl(url);
        when(users.findById(11L)).thenReturn(Optional.of(viewer));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk());
    }

    @Test
    void profileMediaIsVisibleToAnotherActiveUser() throws Exception {
        String url = "/uploads/profiles/12/cover-1772437528169.jpg";
        write(url, png);
        Profile profile = new Profile();
        profile.setCoverPhotoPath(url);
        when(profiles.findByUserId(12L)).thenReturn(Optional.of(profile));
        mvc.perform(get(url).with(viewer())).andExpect(status().isOk());
    }

    @Test
    void unreferencedProfileFileAndWrongOwnerReferenceAreRejected() throws Exception {
        String url = "/uploads/profile-11-1772437528169.png";
        write(url, png);
        mvc.perform(get(url).with(viewer())).andExpect(status().isNotFound());
        profileReference(url.replace("profile-11-", "profile-12-"), false);
        mvc.perform(get(url).with(viewer())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/uploads/test.rtf", "/uploads/arbitrary.png", "/uploads/secret.txt",
            "/uploads/other/file.png", "/uploads/chat/1/extra/" + NAME,
            "/uploads/profiles/11/" + NAME, "/uploads/profiles/11/chat/" + NAME})
    void arbitraryUploadsAndChatThroughProfileRoutesAreDenied(String url) throws Exception {
        write(url, png);
        mvc.perform(get(url).with(viewer())).andExpect(status().is4xxClientError());
    }

    @Test
    void unsupportedHttpMethodsCannotReachStaticMedia() throws Exception {
        mvc.perform(post(CHAT_URL).with(viewer())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidAndUnsupportedImageContentIsNotServed() throws Exception {
        for (byte[] bytes : List.of("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(),
                "<html>synthetic</html>".getBytes(), new byte[0], new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
            write(CHAT_URL, bytes);
            mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound()).andExpect(content().string(""));
        }
    }

    @Test
    void directoriesAreNeverServedAsImages() throws Exception {
        Path path = storage.resolve("chat/1/" + NAME);
        Files.delete(path);
        Files.createDirectory(path);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void oversizedStoredImageIsRejectedBeforeDecoding() throws Exception {
        try (var file = FileChannel.open(storage.resolve("chat/1/" + NAME), StandardOpenOption.WRITE)) {
            file.position(20L * 1024 * 1024);
            file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void excessiveImageDimensionsAreRejectedBeforePixelAllocation() throws Exception {
        byte[] oversizedHeader = png.clone();
        ByteBuffer.wrap(oversizedHeader).putInt(16, 16_385);
        CRC32 crc = new CRC32();
        crc.update(oversizedHeader, 12, 17);
        ByteBuffer.wrap(oversizedHeader).putInt(29, (int) crc.getValue());
        write(CHAT_URL, oversizedHeader);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isNotFound());
    }

    @Test
    void validJpegIsServedWithVerifiedType() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        write(CHAT_URL, output.toByteArray());
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void validWebpIsDecodedAndServedWithVerifiedType() throws Exception {
        // Synthetic 1x1 VP8 image; no production media fixtures.
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        write(CHAT_URL, webp);
        mvc.perform(get(CHAT_URL).with(viewer())).andExpect(status().isOk())
                .andExpect(content().contentType("image/webp")).andExpect(content().bytes(webp));
    }

    @Test
    void publicPagesAndJavascriptRemainPublic() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
    }
}
