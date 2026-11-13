package com.unimatelk.config;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatChannelInterceptorTest {
    private AppUserRepository users;
    private ChatService chat;
    private ChatChannelInterceptor interceptor;
    private AppUser user;
    private static final String EMAIL = "synthetic-chat@example.invalid";

    @BeforeEach
    void setup() {
        users = mock(AppUserRepository.class);
        chat = mock(ChatService.class);
        interceptor = new ChatChannelInterceptor(chat, new CurrentUserService(users, new AppProps()));
        user = new AppUser();
        user.setId(11L);
        user.setStatus("ACTIVE");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(chat.isMember(1L, 11L)).thenReturn(true);
    }

    static OAuth2AuthenticationToken session(String registration) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
        var principal = new DefaultOAuth2User(authorities, Map.of("email", EMAIL, "name", "Synthetic sender"), "email");
        return new OAuth2AuthenticationToken(principal, authorities, registration);
    }

    static Message<byte[]> frame(StompCommand command, String destination, Principal principal) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("synthetic-session");
        headers.setUser(principal);
        headers.setSessionAttributes(ChatSessionTestSupport.attributes(principal));
        if (destination != null) headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private void denied(Message<?> frame) {
        var exception = assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame, null));
        assertEquals("Chat operation denied", exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "google"})
    void bothExistingSessionShapesCanConnectSubscribeAndSend(String registration) {
        for (var command : List.of(StompCommand.CONNECT, StompCommand.STOMP, StompCommand.SUBSCRIBE, StompCommand.SEND)) {
            String destination = command == StompCommand.SUBSCRIBE ? "/topic/rooms/1"
                    : command == StompCommand.SEND ? "/app/chat.send/1" : null;
            var message = frame(command, destination, session(registration));
            assertSame(message, interceptor.preSend(message, null));
        }
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/2", "/app/chat.send/2"})
    void nonMemberCannotSubscribeOrSend(String destination) {
        denied(frame(destination.startsWith("/topic") ? StompCommand.SUBSCRIBE : StompCommand.SEND,
                destination, session("local")));
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"CONNECT", "STOMP", "SUBSCRIBE", "SEND"})
    void anonymousCannotPerformProtectedOperations(StompCommand command) {
        denied(frame(command, command == StompCommand.SUBSCRIBE ? "/topic/rooms/1" : "/app/chat.send/1", null));
        verifyNoInteractions(users, chat);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"BANNED", "TEMP_BLOCKED", "PENDING", "DELETED"})
    void inactiveAccountsCannotConnectSubscribeOrSend(String status) {
        user.setStatus(status);
        denied(frame(StompCommand.CONNECT, null, session("local")));
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", session("google")));
        denied(frame(StompCommand.SEND, "/app/chat.send/1", session("local")));
    }

    @Test
    void missingAccountCannotProvisionOrSubscribe() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", session("local")));
        verify(users, never()).save(any());
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"*", "**", "{id}", "abc", "-1", "0", "+1", "01", "1/", "1/2", "nested/1",
            "1?x=y", "1#x", " 1", "1 ", "1\n", "1%2f2", "1\\2", "9223372036854775808", "99999999999999999999"})
    void roomIdsAreExactCanonicalPositiveLongs(String suffix) {
        denied(frame(StompCommand.SUBSCRIBE, suffix == null ? null : "/topic/rooms/" + suffix, session("local")));
        denied(frame(StompCommand.SEND, suffix == null ? null : "/app/chat.send/" + suffix, session("local")));
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/1", "/topic/rooms/*", "/topic/other", "/queue/test", "/user/queue/test",
            "/app/chat.send", "/app/chat.send/1/other", "/app/other/1", "/app/chatXsend/1", "", "chat.send/1"})
    void clientCannotSendToBrokerOrUnsupportedApplicationDestinations(String destination) {
        denied(frame(StompCommand.SEND, destination, session("local")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms", "/topic", "/topic/**", "/topic/rooms/1/**", "/queue/rooms/1",
            "/user/queue/test", "/user/topic/rooms/1", "/app/chat.send/1", "/other/1", ""})
    void unsupportedSubscriptionsAreDenied(String destination) {
        denied(frame(StompCommand.SUBSCRIBE, destination, session("google")));
    }

    @Test
    void largestLongIdCanBeAuthorized() {
        when(chat.isMember(Long.MAX_VALUE, 11L)).thenReturn(true);
        var message = frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + Long.MAX_VALUE, session("google"));
        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void authorizationIsRecheckedAfterAccountOrMembershipChanges() {
        var subscription = frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", session("local"));
        assertSame(subscription, interceptor.preSend(subscription, null));
        when(chat.isMember(1L, 11L)).thenReturn(false);
        denied(frame(StompCommand.SEND, "/app/chat.send/1", session("local")));
        user.setStatus("BANNED");
        denied(subscription);
    }

    @Test
    void unsupportedUntrustedAndUnauthenticatedPrincipalsAreDenied() {
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", () -> EMAIL));
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1",
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, "unused", List.of())));
        var unauthenticated = session("local");
        unauthenticated.setAuthenticated(false);
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", unauthenticated));
        var anonymous = new AnonymousAuthenticationToken("synthetic", session("local").getPrincipal(),
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", anonymous));
        verifyNoInteractions(users, chat);
    }

    @Test
    void missingMalformedAndBlankEmailAttributesAreDenied() {
        for (Map<String, Object> attributes : List.<Map<String, Object>>of(Map.of("sub", "synthetic"),
                Map.of("sub", "synthetic", "email", 123), Map.of("sub", "synthetic", "email", " "))) {
            var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), attributes, "sub");
            denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1",
                    new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google")));
        }
        verifyNoInteractions(users, chat);
    }

    @Test
    void repositoryFailuresDoNotLeakDetailsOrAllowMessages() {
        when(users.findByEmail(EMAIL)).thenThrow(new IllegalStateException("synthetic private account detail"));
        denied(frame(StompCommand.SUBSCRIBE, "/topic/rooms/1", session("local")));
        doReturn(Optional.of(user)).when(users).findByEmail(EMAIL);
        when(chat.isMember(1L, 11L)).thenThrow(new IllegalStateException("synthetic private room detail"));
        denied(frame(StompCommand.SEND, "/app/chat.send/1", session("local")));
    }

    @Test
    void malformedDuplicateAndInconsistentHeadersFailClosed() {
        var duplicate = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        duplicate.setUser(session("local"));
        duplicate.setDestination("/topic/rooms/1");
        duplicate.addNativeHeader("destination", "/topic/rooms/2");
        denied(MessageBuilder.createMessage(new byte[0], duplicate.getMessageHeaders()));
        var inconsistent = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        inconsistent.setUser(session("local"));
        inconsistent.setDestination("/topic/rooms/1");
        inconsistent.setNativeHeader("destination", "/topic/rooms/2");
        denied(MessageBuilder.createMessage(new byte[0], inconsistent.getMessageHeaders()));
        denied(MessageBuilder.withPayload(new byte[0]).setHeader("stompCommand", StompCommand.SUBSCRIBE)
                .setHeader("simpDestination", 123).build());
    }

    @Test
    void nativeLoginHeadersCannotCreateAuthentication() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setLogin(EMAIL);
        headers.setPasscode("synthetic-unused");
        denied(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }

    @Test
    void ordinaryCleanupAndHeartbeatRemainAllowedWithoutAnActiveAccount() {
        for (var command : List.of(StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT)) {
            var message = frame(command, null, null);
            assertSame(message, interceptor.preSend(message, null));
        }
        var heartbeat = MessageBuilder.createMessage(new byte[]{'\n'}, StompHeaderAccessor.createForHeartbeat().getMessageHeaders());
        assertSame(heartbeat, interceptor.preSend(heartbeat, null));
        verifyNoInteractions(users, chat);
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"ACK", "NACK", "BEGIN", "COMMIT", "ABORT", "CONNECTED", "MESSAGE", "RECEIPT", "ERROR"})
    void unsupportedCommandsAreDenied(StompCommand command) {
        denied(frame(command, null, session("local")));
    }

    @Test
    void absentCommandDoesNotBypassAuthorizationForNonHeartbeatMessages() {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/topic/rooms/1");
        denied(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }
}
