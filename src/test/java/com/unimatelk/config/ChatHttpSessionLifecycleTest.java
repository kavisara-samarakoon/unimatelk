package com.unimatelk.config;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import jakarta.servlet.http.HttpSessionBindingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.DefaultSockJsService;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatHttpSessionLifecycleTest {
    private Authentication principal;
    private MockHttpSession http;
    private ChatHttpSessionBinding binding;
    private AppUserRepository users;
    private ChatService chat;
    private ChatOutboundAuthorization guard;
    private WebSocketHandler delegate;
    private WebSocketHandler transport;
    private WebSocketSession raw;
    private WebSocketSession gated;

    @BeforeEach
    void setup() throws Exception {
        principal = ChatChannelInterceptorTest.session("local");
        http = ChatSessionTestSupport.http(principal);
        binding = ChatHttpSessionBinding.bind(http);
        users = mock(AppUserRepository.class);
        chat = mock(ChatService.class);
        guard = new ChatOutboundAuthorization(new CurrentUserService(users, new AppProps()), chat);
        delegate = mock(WebSocketHandler.class);
        transport = guard.decorate(delegate);
        var captured = new AtomicReference<WebSocketSession>();
        doAnswer(invocation -> { captured.set(invocation.getArgument(0)); return null; })
                .when(delegate).afterConnectionEstablished(any());
        raw = socket("synthetic-socket", binding);
        transport.afterConnectionEstablished(raw);
        gated = captured.get();
        var user = new AppUser();
        user.setId(11L);
        user.setStatus("ACTIVE");
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.of(user));
        when(chat.isMember(1L, 11L)).thenReturn(true);
    }

    private WebSocketSession socket(String id, ChatHttpSessionBinding binding) {
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.getPrincipal()).thenReturn(principal);
        when(socket.getAttributes()).thenReturn(Map.of(ChatHttpSessionBinding.ATTRIBUTE, binding));
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private int registrations(Object owner, String field) {
        return ((Map<?, ?>) ReflectionTestUtils.getField(owner, field)).size();
    }

    @Test
    void invalidationBlocksFinalTransportWritesAndCleansRegistriesEvenWithoutCloseCallback() throws Exception {
        gated.sendMessage(new TextMessage("Synthetic private content"));
        verify(raw).sendMessage(any());
        clearInvocations(raw);
        http.invalidate(); // Real MockHttpSession unbinding, not a direct call to the registry.
        gated.sendMessage(new TextMessage("Synthetic post-logout content"));
        verify(raw, never()).sendMessage(any());
        verify(raw).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(0, registrations(guard, "sessions"));
        assertEquals(0, registrations(binding, "connections"));
    }

    @Test
    void duplicateUnbindingAndDisconnectAreIdempotent() throws Exception {
        http.invalidate();
        binding.valueUnbound(new HttpSessionBindingEvent(http, ChatHttpSessionBinding.ATTRIBUTE));
        transport.afterConnectionClosed(raw, CloseStatus.NORMAL);
        transport.afterConnectionClosed(raw, CloseStatus.NORMAL);
        verify(raw).close(CloseStatus.POLICY_VIOLATION);
        assertFalse(binding.active(principal));
        assertEquals(0, registrations(binding, "connections"));
    }

    @Test
    void detachedTransportCannotWriteEvenWhenItsHttpSessionIsStillValid() throws Exception {
        transport.afterConnectionClosed(raw, CloseStatus.NORMAL);
        assertTrue(binding.active(principal));
        gated.sendMessage(new TextMessage("Synthetic stale transport content"));
        verify(raw, never()).sendMessage(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedOrInterruptedCloseDoesNotRestoreAccessOrPreventOtherSocketClosure(boolean interrupted) throws Exception {
        var second = socket("synthetic-second", binding);
        transport.afterConnectionEstablished(second);
        doThrow(interrupted ? new InterruptedIOException("Synthetic interruption") : new IOException("Synthetic failure"))
                .when(raw).close(CloseStatus.POLICY_VIOLATION);
        try {
            assertDoesNotThrow(http::invalidate);
            assertEquals(interrupted, Thread.currentThread().isInterrupted());
            verify(second).close(CloseStatus.POLICY_VIOLATION);
            gated.sendMessage(new TextMessage("Synthetic private content"));
            verify(raw, never()).sendMessage(any());
            assertEquals(0, registrations(guard, "sessions"));
            assertEquals(0, registrations(binding, "connections"));
        } finally { Thread.interrupted(); }
    }

    @Test
    void staleHandshakeCannotRegisterAfterInvalidationButFreshSessionCan() throws Exception {
        http.invalidate();
        var late = socket("synthetic-late", binding);
        assertThrows(IllegalStateException.class, () -> transport.afterConnectionEstablished(late));
        assertEquals(0, registrations(guard, "sessions"));
        var fresh = ChatSessionTestSupport.http(principal);
        var freshBinding = ChatHttpSessionBinding.bind(fresh);
        transport.afterConnectionEstablished(socket("synthetic-fresh", freshBinding));
        assertTrue(freshBinding.active(principal));
        assertFalse(binding.active(principal));
    }

    @Test
    void sessionIdRotationPreservesAssociationAndClearedAuthenticationBlocksAccess() throws Exception {
        http.changeSessionId();
        assertTrue(binding.active(principal));
        gated.sendMessage(new TextMessage("Synthetic private content"));
        verify(raw).sendMessage(any());
        ((SecurityContext) http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .setAuthentication(null);
        assertFalse(binding.active(principal));
        clearInvocations(raw);
        gated.sendMessage(new TextMessage("Synthetic post-clear content"));
        verify(raw, never()).sendMessage(any());
    }

    @Test
    void removedBindingCannotBeResurrectedByRebindingOrNativeHeaders() {
        http.removeAttribute(ChatHttpSessionBinding.ATTRIBUTE);
        assertFalse(binding.active(principal));
        var replacement = ChatHttpSessionBinding.bind(http);
        assertNotSame(binding, replacement);
        assertTrue(replacement.active(principal));
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(principal);
        headers.setDestination("/topic/rooms/1");
        headers.setNativeHeader("httpSessionId", http.getId());
        headers.setNativeHeader(ChatHttpSessionBinding.ATTRIBUTE, http.getId());
        var inbound = new ChatChannelInterceptor(chat, new CurrentUserService(users, new AppProps()));
        assertThrows(AccessDeniedException.class, () -> inbound.preSend(
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
    }

    @Test
    void invalidationDuringOutboundDatabaseLookupIsRecheckedBeforeHandling() {
        when(chat.isMember(1L, 11L)).thenAnswer(invocation -> { http.invalidate(); return true; });
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/topic/rooms/1");
        headers.setSessionId(raw.getId());
        assertNull(guard.beforeHandle(MessageBuilder.createMessage("Synthetic private content", headers.getMessageHeaders()), null, null));
    }

    @Test
    void invalidationDuringInboundMembershipLookupRejectsTheOperation() {
        when(chat.isMember(1L, 11L)).thenAnswer(invocation -> { http.invalidate(); return true; });
        var headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/app/chat.send/1");
        headers.setUser(principal);
        headers.setSessionAttributes(raw.getAttributes());
        var inbound = new ChatChannelInterceptor(chat, new CurrentUserService(users, new AppProps()));
        assertThrows(AccessDeniedException.class, () -> inbound.preSend(
                MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
    }

    @Test
    void authorizationCompletedBeforeLogoutCannotBypassTheFinalWriteGate() throws Exception {
        try (var workers = Executors.newSingleThreadExecutor()) {
            var ready = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var write = workers.submit(() -> {
                var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                headers.setSessionId(raw.getId());
                headers.setDestination("/topic/rooms/1");
                assertNotNull(guard.beforeHandle(MessageBuilder.createMessage(
                        "Synthetic private content", headers.getMessageHeaders()), null, null));
                ready.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                gated.sendMessage(new TextMessage("Synthetic private content"));
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            try { http.invalidate(); }
            finally { release.countDown(); }
            write.get(5, TimeUnit.SECONDS);
            verify(raw, never()).sendMessage(any());
        }
    }

    @Test
    void invalidationRacingTransportEstablishmentCannotRetainOrAuthorizeIt() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(delegate).afterConnectionEstablished(any());
        try (var workers = Executors.newSingleThreadExecutor()) {
            var registration = workers.submit(() -> {
                assertThrows(IllegalStateException.class,
                        () -> transport.afterConnectionEstablished(socket("synthetic-racing", binding)));
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            try { http.invalidate(); }
            finally { release.countDown(); }
            registration.get(5, TimeUnit.SECONDS);
            assertEquals(0, registrations(guard, "sessions"));
            assertEquals(0, registrations(binding, "connections"));
        }
    }

    @Test
    void slowCloseDoesNotHoldRegistryLocksOrBlockIndependentSessions() throws Exception {
        var closing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { closing.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(raw).close(CloseStatus.POLICY_VIOLATION);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var logout = workers.submit(http::invalidate);
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            try {
                assertFalse(binding.register(socket("synthetic-denied", binding), () -> {}));
                var other = ChatHttpSessionBinding.bind(ChatSessionTestSupport.http(principal));
                transport.afterConnectionEstablished(socket("synthetic-independent", other));
                assertTrue(other.active(principal));
            } finally { release.countDown(); }
            logout.get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/websocket", "/000/synthetic/websocket"})
    void actualSockJsAndRawWebSocketRoutingInvokeTheSessionHandshake(String path) throws Exception {
        var captured = new AtomicReference<Map<String, Object>>();
        HandshakeHandler upgrade = (request, response, handler, attributes) -> { captured.set(attributes); return true; };
        var service = new DefaultSockJsService(mock(TaskScheduler.class), new WebSocketTransportHandler(upgrade));
        service.setHandshakeInterceptors(List.of(new ChatSessionHandshakeInterceptor()));
        var request = new MockHttpServletRequest("GET", "/ws" + path);
        request.addHeader("Upgrade", "websocket");
        request.setUserPrincipal(principal);
        request.setSession(http);
        service.handleRequest(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), path, mock(WebSocketHandler.class));
        assertNotNull(captured.get());
        assertTrue(ChatHttpSessionBinding.active(captured.get(), principal));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalidated", "different-user"})
    void handshakeRejectsMissingInvalidatedOrMismatchedSessionWithoutCreatingOne(String condition) {
        var request = new MockHttpServletRequest("GET", "/ws/websocket");
        request.setUserPrincipal(principal);
        if (!condition.equals("missing")) request.setSession(http);
        if (condition.equals("invalidated")) http.invalidate();
        if (condition.equals("different-user")) request.setUserPrincipal(() -> "another-synthetic-user");
        request.addHeader("JSESSIONID", http.getId());
        Map<String, Object> attributes = new HashMap<>();
        var response = new MockHttpServletResponse();
        assertFalse(new ChatSessionHandshakeInterceptor().beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes));
        assertEquals(403, response.getStatus());
        assertTrue(attributes.isEmpty());
        if (condition.equals("missing")) assertNull(request.getSession(false));
    }
}
