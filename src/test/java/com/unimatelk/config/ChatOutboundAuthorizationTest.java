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
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatOutboundAuthorizationTest {
    private AppUserRepository users;
    private ChatService chat;
    private ChatOutboundAuthorization guard;
    private WebSocketHandler delegate;
    private WebSocketHandler transport;
    private WebSocketSession session;
    private AppUser user;

    @BeforeEach
    void setup() throws Exception {
        users = mock(AppUserRepository.class);
        chat = mock(ChatService.class);
        guard = new ChatOutboundAuthorization(new CurrentUserService(users, new AppProps()), chat);
        delegate = mock(WebSocketHandler.class);
        transport = guard.decorate(delegate);
        session = session("recipient", ChatChannelInterceptorTest.session("local"));
        transport.afterConnectionEstablished(session);
        user = new AppUser();
        user.setId(11L);
        user.setStatus("ACTIVE");
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.of(user));
        when(chat.isMember(1L, 11L)).thenReturn(true);
    }

    private WebSocketSession session(String id, Principal principal) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getAttributes()).thenReturn(ChatSessionTestSupport.attributes(principal));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Message<?> message(String id, String destination) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        if (id != null) headers.setSessionId(id);
        if (destination != null) headers.setDestination(destination);
        headers.setSubscriptionId("room-sub");
        return MessageBuilder.createMessage("Synthetic private content", headers.getMessageHeaders());
    }

    private Message<?> authorize(Message<?> message) {
        return guard.beforeHandle(message, null, null);
    }

    private int tracked() {
        return ((Map<?, ?>) ReflectionTestUtils.getField(guard, "sessions")).size();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "google"})
    void activeSessionPrincipalsCanReceiveRoomMessages(String registration) throws Exception {
        var another = session("another", ChatChannelInterceptorTest.session(registration));
        transport.afterConnectionEstablished(another);
        var message = message("another", "/topic/rooms/1");
        assertSame(message, authorize(message));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"BANNED", "TEMP_BLOCKED", "PENDING", "DELETED"})
    void accountStatusIsRecheckedForEveryDelivery(String status) {
        var message = message("recipient", "/topic/rooms/1");
        assertSame(message, authorize(message));
        user.setStatus(status);
        assertNull(authorize(message));
        verify(users, times(2)).findByEmail("synthetic-chat@example.invalid");
        verify(users, never()).save(any());
    }

    @Test
    void membershipIsRecheckedAcrossAllConnectionsWithoutCachingPermissions() throws Exception {
        var another = session("another", ChatChannelInterceptorTest.session("google"));
        transport.afterConnectionEstablished(another);
        for (String id : new String[]{"recipient", "another"}) assertNotNull(authorize(message(id, "/topic/rooms/1")));
        when(chat.isMember(1L, 11L)).thenReturn(false);
        for (String id : new String[]{"recipient", "another"}) assertNull(authorize(message(id, "/topic/rooms/1")));
        verify(users, times(4)).findByEmail("synthetic-chat@example.invalid");
        verify(chat, times(4)).isMember(1L, 11L);
    }

    @Test
    void accountDeletionAndRepositoryErrorsFailClosedWithoutThrowingOrCachingDenials() {
        var message = message("recipient", "/topic/rooms/1");
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.empty());
        assertNull(authorize(message));
        doThrow(new IllegalStateException("Synthetic private account detail"))
                .when(users).findByEmail("synthetic-chat@example.invalid");
        assertNull(assertDoesNotThrow(() -> authorize(message)));
        doReturn(Optional.of(user)).when(users).findByEmail("synthetic-chat@example.invalid");
        when(chat.isMember(1L, 11L)).thenThrow(new IllegalStateException("Synthetic private membership detail"));
        assertNull(assertDoesNotThrow(() -> authorize(message)));
        doReturn(true).when(chat).isMember(1L, 11L);
        assertSame(message, authorize(message));
    }

    @Test
    void missingRecipientIdentityCannotBorrowAnOutboundSenderPrincipal() throws Exception {
        var anonymous = session("anonymous", null);
        assertThrows(IllegalStateException.class, () -> transport.afterConnectionEstablished(anonymous));
        var headers = SimpMessageHeaderAccessor.wrap(message("anonymous", "/topic/rooms/1"));
        headers.setUser(ChatChannelInterceptorTest.session("local"));
        assertNull(authorize(MessageBuilder.createMessage("Synthetic private content", headers.getMessageHeaders())));
        assertNull(authorize(message(null, "/topic/rooms/1")));
        assertNull(authorize(message("missing", "/topic/rooms/1")));
        verifyNoInteractions(users, chat);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/topic/rooms/0", "/topic/rooms/-1", "/topic/rooms/01", "/topic/rooms/*",
            "/topic/rooms/1/extra", "/topic/rooms/9223372036854775808", "/queue/test", "/user/queue/test"})
    void unclassifiedOrMalformedPrivateDestinationsFailClosed(String destination) {
        assertNull(authorize(message("recipient", destination)));
        verifyNoInteractions(users, chat);
    }

    @Test
    void malformedHeadersAndMissingMessageTypesFailClosed() {
        assertNull(authorize(MessageBuilder.withPayload("Synthetic private content").build()));
        assertNull(authorize(MessageBuilder.withPayload("Synthetic private content")
                .setHeader("simpMessageType", "not-a-type").build()));
        assertNull(authorize(MessageBuilder.withPayload("Synthetic private content")
                .setHeader("simpMessageType", SimpMessageType.MESSAGE).setHeader("simpDestination", 123).build()));
    }

    @ParameterizedTest
    @EnumSource(value = SimpMessageType.class, names = {"CONNECT_ACK", "DISCONNECT_ACK", "HEARTBEAT", "OTHER"})
    void protocolAndCleanupFramesDoNotQueryAuthorization(SimpMessageType type) {
        var headers = SimpMessageHeaderAccessor.create(type);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertSame(message, authorize(message));
        verifyNoInteractions(users, chat);
    }

    @Test
    void closedOrRemovedTransportCannotReceiveAlreadyQueuedMessages() throws Exception {
        var message = message("recipient", "/topic/rooms/1");
        assertSame(message, authorize(message));
        transport.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertNull(authorize(message));
        assertEquals(0, tracked());
        transport.afterConnectionEstablished(session);
        when(session.isOpen()).thenReturn(false);
        assertNull(authorize(message));
    }

    @Test
    void closingTransportDuringAuthorizationDropsTheMessage() {
        when(chat.isMember(1L, 11L)).thenAnswer(invocation -> {
            transport.afterConnectionClosed(session, CloseStatus.NORMAL);
            return true;
        });
        assertNull(authorize(message("recipient", "/topic/rooms/1")));
    }

    @Test
    void repeatedSessionLifecyclesAndTransportErrorsDoNotRetainState() throws Exception {
        for (int i = 0; i < 100; i++) {
            var next = session("cycle-" + i, session.getPrincipal());
            transport.afterConnectionEstablished(next);
            assertEquals(2, tracked());
            transport.afterConnectionClosed(next, CloseStatus.NORMAL);
            assertEquals(1, tracked());
        }
        transport.handleTransportError(session, new IllegalStateException("Synthetic transport failure"));
        assertEquals(0, tracked());
        assertNull(authorize(message("recipient", "/topic/rooms/1")));
        guard.clearSessions();
        assertEquals(0, tracked());
    }

    @Test
    void failedConnectionRegistrationAndFailingCloseDelegateStillCleanState() throws Exception {
        var failing = session("failing", session.getPrincipal());
        doThrow(new IllegalStateException("Synthetic establishment failure")).when(delegate).afterConnectionEstablished(any(WebSocketSession.class));
        assertThrows(IllegalStateException.class, () -> transport.afterConnectionEstablished(failing));
        assertEquals(1, tracked());
        doThrow(new IllegalStateException("Synthetic close failure")).when(delegate).afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThrows(IllegalStateException.class, () -> transport.afterConnectionClosed(session, CloseStatus.NORMAL));
        assertEquals(0, tracked());
    }

    @Test
    void duplicateRegistrationAndLateCloseCannotEvictAnotherLiveTransport() throws Exception {
        var replacement = session("recipient", session.getPrincipal());
        assertThrows(IllegalStateException.class, () -> transport.afterConnectionEstablished(replacement));
        transport.afterConnectionClosed(replacement, CloseStatus.NORMAL);
        assertNotNull(authorize(message("recipient", "/topic/rooms/1")));
        transport.afterConnectionClosed(session, CloseStatus.NORMAL);
        transport.afterConnectionEstablished(replacement);
        transport.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertNotNull(authorize(message("recipient", "/topic/rooms/1")));
        guard.clearSessions();
        assertEquals(0, tracked());
    }
}
