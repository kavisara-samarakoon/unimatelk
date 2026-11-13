package com.unimatelk.config;

import com.unimatelk.api.ChatDtos;
import com.unimatelk.api.ChatWsController;
import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ChatMember;
import com.unimatelk.domain.ChatRoom;
import com.unimatelk.repo.*;
import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.server.support.HandshakeInterceptorChain;
import org.springframework.web.socket.sockjs.transport.TransportHandlingSockJsService;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import jakarta.servlet.http.HttpSession;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses production broker configuration, interceptor, handler and ChatService. No server or database starts. */
class ChatBrokerSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private AbstractSubscribableChannel inbound;
    private final List<Message<?>> outbound = new ArrayList<>();
    private AppUserRepository users;
    private ChatMemberRepository members;
    private MessageRepository messages;
    private AppUser sender;
    private ChatRoom room;
    private final Map<String, WebSocketSession> transportSessions = new HashMap<>();

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({WebSocketConfig.class, ChatChannelInterceptor.class, ChatOutboundAuthorization.class, CurrentUserService.class,
            ChatService.class, ChatWsController.class, SecurityConfig.class, com.unimatelk.api.LocalAuthController.class,
            com.unimatelk.api.CsrfController.class})
    static class Fixture implements WebSocketMessageBrokerConfigurer {
        final HoldingExecutor outboundExecutor = new HoldingExecutor();
        final HoldingExecutor inboundExecutor = new HoldingExecutor();
        @Bean AppProps props() { return new AppProps(); }
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean ChatMemberRepository members() { return mock(ChatMemberRepository.class); }
        @Bean ChatRoomRepository rooms() { return mock(ChatRoomRepository.class); }
        @Bean MessageRepository messages() { return mock(MessageRepository.class); }
        @Bean BlockRepository blocks() { return mock(BlockRepository.class); }
        @Override public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.executor(inboundExecutor);
        }
        @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
            registration.executor(outboundExecutor);
        }
    }

    static class HoldingExecutor implements TaskExecutor {
        boolean hold;
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) {
            if (hold) tasks.add(task);
            else task.run();
        }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fixture.class);
        context.refresh();
        inbound = context.getBean("clientInboundChannel", AbstractSubscribableChannel.class);
        context.getBean("clientOutboundChannel", AbstractSubscribableChannel.class).subscribe(outbound::add);
        users = context.getBean(AppUserRepository.class);
        members = context.getBean(ChatMemberRepository.class);
        messages = context.getBean(MessageRepository.class);
        sender = new AppUser();
        sender.setId(11L);
        sender.setStatus("ACTIVE");
        sender.setName("Synthetic sender");
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.of(sender));
        room = new ChatRoom();
        ReflectionTestUtils.setField(room, "id", 1L);
        when(context.getBean(ChatRoomRepository.class).findById(1L)).thenReturn(Optional.of(room));
        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(true);
        when(members.existsByRoomAndUser(room, sender)).thenReturn(true);
        ChatMember member = new ChatMember();
        member.setUser(sender);
        when(members.findByRoom(room)).thenReturn(List.of(member));
        when(messages.save(any(com.unimatelk.domain.Message.class))).thenAnswer(invocation -> {
            var message = invocation.getArgument(0, com.unimatelk.domain.Message.class);
            ReflectionTestUtils.setField(message, "id", 123L);
            return message;
        });
    }

    @AfterEach
    void close() throws Exception {
        if (context != null) {
            try {
                for (var session : transportSessions.values()) {
                    transportHandler().afterConnectionClosed(session, CloseStatus.NORMAL);
                }
            } finally {
                context.close();
            }
        }
    }

    private Message<byte[]> wire(String command, String sessionId, Principal principal, String headers, String body) {
        String frame = command + "\n" + (headers.isEmpty() ? "" : headers + "\n") + "\n" + body + "\0";
        var decoded = new StompDecoder().decode(ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8))).getFirst();
        var accessor = SimpMessageHeaderAccessor.wrap(decoded);
        // Only the transport supplies these headers from the HTTP session; native STOMP login is irrelevant.
        accessor.setSessionId(sessionId);
        accessor.setSessionAttributes(transportSessions.containsKey(sessionId)
                ? transportSessions.get(sessionId).getAttributes() : new HashMap<>());
        accessor.setUser(principal);
        return MessageBuilder.createMessage(decoded.getPayload(), accessor.getMessageHeaders());
    }

    private void connect(String session, Principal principal) {
        connect(session, principal, httpSession(principal));
    }

    private MockHttpSession httpSession(Principal principal) {
        var session = new MockHttpSession(context.getServletContext());
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication((Authentication) principal);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
        return session;
    }

    private Map<String, Object> handshake(HttpSession http, Principal principal) throws Exception {
        var request = new MockHttpServletRequest(context.getServletContext(), "GET", "/ws/000/synthetic/websocket");
        request.setSession(http);
        request.setUserPrincipal(principal);
        var mapping = context.getBean("stompWebSocketHandlerMapping", SimpleUrlHandlerMapping.class);
        var endpoint = (SockJsHttpRequestHandler) mapping.getUrlMap().get("/ws/**");
        var service = (TransportHandlingSockJsService) endpoint.getSockJsService();
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(new HandshakeInterceptorChain(service.getHandshakeInterceptors(), transportHandler())
                .applyBeforeHandshake(new ServletServerHttpRequest(request),
                        new ServletServerHttpResponse(new MockHttpServletResponse()), attributes));
        return attributes;
    }

    private void connect(String session, Principal principal, HttpSession http) {
        var transport = mock(WebSocketSession.class);
        when(transport.getId()).thenReturn(session);
        when(transport.getPrincipal()).thenReturn(principal);
        when(transport.getAcceptedProtocol()).thenReturn("v12.stomp");
        when(transport.isOpen()).thenReturn(true);
        try {
            when(transport.getAttributes()).thenReturn(handshake(http, principal));
            // Exercise the production transport decorator; no network socket is opened.
            transportHandler().afterConnectionEstablished(transport);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        transportSessions.put(session, transport);
        assertTrue(inbound.send(wire("CONNECT", session, principal, "accept-version:1.2\nheart-beat:0,0", "")));
    }

    private WebSocketHandler transportHandler() {
        var mapping = context.getBean("stompWebSocketHandlerMapping", SimpleUrlHandlerMapping.class);
        return ((SockJsHttpRequestHandler) mapping.getUrlMap().get("/ws/**")).getWebSocketHandler();
    }

    private Message<byte[]> subscribe(String session, Principal principal, String destination) {
        return wire("SUBSCRIBE", session, principal, "id:room-sub\ndestination:" + destination, "");
    }

    private Message<byte[]> send(String session, Principal principal, String destination) {
        return wire("SEND", session, principal, "destination:" + destination + "\ncontent-type:application/json", "{\"content\":\" Hello synthetic room \"}");
    }

    private List<Message<?>> delivered() {
        return outbound.stream().filter(m -> m.getHeaders().get("simpMessageType") == SimpMessageType.MESSAGE).toList();
    }

    private MockMvc httpClient() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"logout-TEXT", "logout-IMAGE", "invalidate-TEXT", "invalidate-IMAGE", "expire-TEXT", "expire-IMAGE"})
    void httpRevocationStopsAllOriginatingSocketsButPreservesIndependentSession(String scenario) throws Exception {
        var principal = ChatChannelInterceptorTest.session("local");
        var http = httpSession(principal);
        StandardSession expiring = null;
        HttpSession originating = http;
        if (scenario.startsWith("expire")) {
            var manager = new StandardManager();
            manager.setContext(new StandardContext());
            expiring = new StandardSession(manager);
            expiring.setCreationTime(System.currentTimeMillis());
            expiring.setId("synthetic-expiring-session");
            expiring.setValid(true);
            expiring.setMaxInactiveInterval(1);
            originating = expiring.getSession();
            originating.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        }
        connect("revoked-one", principal, originating);
        connect("revoked-two", principal, originating);
        connect("independent", principal, httpSession(principal));
        for (String id : List.of("revoked-one", "revoked-two", "independent")) {
            inbound.send(subscribe(id, principal, "/topic/rooms/1"));
        }
        broadcast("TEXT");
        assertEquals(3, delivered().size());
        outbound.clear();
        if (scenario.startsWith("logout")) {
            httpClient().perform(post("/logout").session(http).with(csrf())).andExpect(status().is3xxRedirection());
            assertTrue(http.isInvalid());
        } else if (expiring != null) {
            ReflectionTestUtils.setField(expiring, "lastAccessedTime", System.currentTimeMillis() - 5000);
            ReflectionTestUtils.setField(expiring, "thisAccessedTime", System.currentTimeMillis() - 5000);
            assertFalse(expiring.isValid()); // Real container idle expiry and binding-listener callbacks.
        } else {
            http.invalidate();
        }
        broadcast(scenario.endsWith("IMAGE") ? "IMAGE" : "TEXT");
        assertEquals(List.of("independent"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"logout-SEND", "logout-SUBSCRIBE", "invalidate-SEND", "invalidate-SUBSCRIBE"})
    void httpRevocationRejectsSubsequentProtectedOperations(String scenario) throws Exception {
        var principal = ChatChannelInterceptorTest.session("local");
        var http = httpSession(principal);
        connect("revoked", principal, http);
        if (scenario.startsWith("logout")) {
            httpClient().perform(post("/logout").session(http).with(csrf())).andExpect(status().is3xxRedirection());
        } else http.invalidate();
        rejected(scenario.endsWith("SEND") ? send("revoked", principal, "/app/chat.send/1")
                : subscribe("revoked", principal, "/topic/rooms/1"));
    }

    @Test
    void logoutRejectsQueuedSendAndSubscriptionAndAllowsFreshLocalLogin() throws Exception {
        var principal = ChatChannelInterceptorTest.session("local");
        var http = httpSession(principal);
        connect("revoked", principal, http);
        var executor = context.getBean(Fixture.class).inboundExecutor;
        executor.hold = true;
        inbound.send(send("revoked", principal, "/app/chat.send/1"));
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        httpClient().perform(post("/logout").session(http).with(csrf())).andExpect(status().is3xxRedirection());
        executor.drain();
        executor.hold = false;
        verify(messages, never()).save(any());
        broadcast("TEXT");
        assertTrue(delivered().isEmpty());

        sender.setEmail("synthetic-chat@example.invalid");
        sender.setName("Synthetic sender");
        sender.setRole("STUDENT");
        sender.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Synthetic-password-123"));
        var login = httpClient().perform(post("/api/auth/login").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"synthetic-chat@example.invalid\",\"password\":\"Synthetic-password-123\"}"))
                .andExpect(status().isOk()).andReturn();
        var freshHttp = login.getRequest().getSession(false);
        var security = (org.springframework.security.core.context.SecurityContext)
                freshHttp.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        connect("fresh-login", security.getAuthentication(), freshHttp);
        inbound.send(subscribe("fresh-login", security.getAuthentication(), "/topic/rooms/1"));
        broadcast("IMAGE");
        assertEquals(List.of("fresh-login"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
    }

    @Test
    void logoutWhileBroadcastIsQueuedDropsBothMessageTypesOnlyForOriginatingSession() throws Exception {
        var principal = ChatChannelInterceptorTest.session("google");
        var http = httpSession(principal);
        connect("revoked", principal, http);
        connect("independent", principal, httpSession(principal));
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        inbound.send(subscribe("independent", principal, "/topic/rooms/1"));
        var executor = context.getBean(Fixture.class).outboundExecutor;
        executor.hold = true;
        broadcast("TEXT");
        broadcast("IMAGE");
        httpClient().perform(post("/logout").session(http).with(csrf())).andExpect(status().is3xxRedirection());
        executor.drain();
        executor.hold = false;
        assertEquals(List.of("independent", "independent"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
    }

    private void subscribeOtherMember() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
        var oauth = new DefaultOAuth2User(authorities,
                Map.of("email", "synthetic-recipient@example.invalid"), "email");
        var principal = new OAuth2AuthenticationToken(oauth, authorities, "google");
        var other = new AppUser();
        other.setId(12L);
        other.setStatus("ACTIVE");
        when(users.findByEmail("synthetic-recipient@example.invalid")).thenReturn(Optional.of(other));
        when(members.existsByRoomIdAndUserId(1L, 12L)).thenReturn(true);
        connect("other-member", principal);
        inbound.send(subscribe("other-member", principal, "/topic/rooms/1"));
    }

    private void broadcast(String type) {
        var dto = new ChatDtos.MessageDto(123L, 1L, 12L, "Synthetic recipient", type,
                "TEXT".equals(type) ? "Synthetic private content" : null,
                "IMAGE".equals(type) ? "/uploads/chat/1/u12-123.png" : null,
                Instant.parse("2026-01-01T00:00:00Z"));
        context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/rooms/1", dto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXT", "IMAGE"})
    void bannedSubscriberDoesNotReceiveLaterBroadcastButOtherMemberDoes(String type) throws Exception {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("revoked", principal);
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        subscribeOtherMember();
        broadcast(type);
        assertEquals(2, delivered().size(), "Both members receive the broadcast before revocation");
        outbound.clear();
        clearInvocations(transportSessions.get("revoked"), transportSessions.get("other-member"));

        sender.setStatus("BANNED");
        broadcast(type);
        assertEquals(List.of("other-member"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
        verify(transportSessions.get("revoked"), never()).sendMessage(any());
        verify(transportSessions.get("other-member")).sendMessage(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXT", "IMAGE"})
    void removedMemberDoesNotReceiveLaterBroadcastButOtherMemberDoes(String type) throws Exception {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("revoked", principal);
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        subscribeOtherMember();
        broadcast(type);
        assertEquals(2, delivered().size(), "Both members receive the broadcast before revocation");
        outbound.clear();
        clearInvocations(transportSessions.get("revoked"), transportSessions.get("other-member"));

        when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(false);
        broadcast(type);
        assertEquals(List.of("other-member"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
        verify(transportSessions.get("revoked"), never()).sendMessage(any());
        verify(transportSessions.get("other-member")).sendMessage(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "membership"})
    void queuedBroadcastIsReauthorizedWhenExecutorHandlesIt(String revocation) {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("revoked", principal);
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        subscribeOtherMember();
        var executor = context.getBean(Fixture.class).outboundExecutor;
        executor.hold = true;
        broadcast("TEXT");
        broadcast("IMAGE");
        assertTrue(delivered().isEmpty());
        assertFalse(executor.tasks.isEmpty());
        if (revocation.equals("account")) sender.setStatus("BANNED");
        else when(members.existsByRoomIdAndUserId(1L, 11L)).thenReturn(false);
        executor.drain();
        assertEquals(List.of("other-member", "other-member"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
        executor.hold = false;
    }

    @Test
    void deletedAccountAndAuthorizationLookupFailuresDropOnlyAffectedRecipients() {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("revoked", principal);
        inbound.send(subscribe("revoked", principal, "/topic/rooms/1"));
        subscribeOtherMember();
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.empty());
        broadcast("TEXT");
        doThrow(new IllegalStateException("Synthetic private repository detail"))
                .when(users).findByEmail("synthetic-chat@example.invalid");
        broadcast("IMAGE");
        doReturn(Optional.of(sender)).when(users).findByEmail("synthetic-chat@example.invalid");
        when(members.existsByRoomIdAndUserId(1L, 11L))
                .thenThrow(new IllegalStateException("Synthetic private membership detail"));
        broadcast("TEXT");
        assertEquals(List.of("other-member", "other-member", "other-member"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).toList());
    }

    private void rejected(Message<?> message) {
        var exception = assertThrows(MessageDeliveryException.class, () -> inbound.send(message));
        assertInstanceOf(AccessDeniedException.class, exception.getCause());
        assertEquals("Chat operation denied", exception.getCause().getMessage());
    }

    @Test
    void realHandlerPersistsTextAndBrokerDeliversOnlyMatchingAuthorizedSubscriptions() {
        var local = ChatChannelInterceptorTest.session("local");
        var google = ChatChannelInterceptorTest.session("google");
        connect("local-session", local);
        connect("google-session", google);
        connect("other-room-session", google);
        inbound.send(subscribe("local-session", local, "/topic/rooms/1"));
        inbound.send(subscribe("google-session", google, "/topic/rooms/1"));
        when(members.existsByRoomIdAndUserId(2L, 11L)).thenReturn(true);
        inbound.send(subscribe("other-room-session", google, "/topic/rooms/2"));
        inbound.send(send("local-session", local, "/app/chat.send/1"));

        var saved = org.mockito.ArgumentCaptor.forClass(com.unimatelk.domain.Message.class);
        verify(messages).save(saved.capture());
        assertEquals("TEXT", saved.getValue().getType());
        assertEquals("Hello synthetic room", saved.getValue().getContent());
        assertSame(room, saved.getValue().getRoom());
        assertSame(sender, saved.getValue().getSender());
        assertEquals(2, delivered().size());
        assertEquals(List.of("google-session", "local-session"), delivered().stream()
                .map(m -> (String) m.getHeaders().get("simpSessionId")).sorted().toList());
        for (var message : delivered()) {
            assertEquals("/topic/rooms/1", message.getHeaders().get("simpDestination"));
            assertEquals("room-sub", message.getHeaders().get("simpSubscriptionId"));
            String json = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
            assertTrue(json.contains("Hello synthetic room"));
            assertTrue(json.contains("\"senderUserId\":11"));
        }
    }

    @Test
    void unauthorizedWildcardAndAnonymousSubscriptionsCannotReceiveServerImageBroadcasts() {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("authorized", principal);
        inbound.send(subscribe("authorized", principal, "/topic/rooms/1"));
        rejected(subscribe("nonmember", principal, "/topic/rooms/2"));
        rejected(subscribe("wildcard", principal, "/topic/rooms/*"));
        rejected(subscribe("anonymous", null, "/topic/rooms/1"));
        rejected(subscribe("user-route", principal, "/user/queue/chat"));
        rejected(subscribe("queue-route", principal, "/queue/chat"));
        rejected(wire("SUBSCRIBE", "duplicate-header", principal,
                "id:room-sub\ndestination:/topic/rooms/1\ndestination:/topic/rooms/2", ""));
        var image = new ChatDtos.MessageDto(123L, 1L, 11L, "Synthetic sender", "IMAGE", null,
                "/uploads/chat/1/u11-123.png", Instant.parse("2026-01-01T00:00:00Z"));
        context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/rooms/1", image);
        assertEquals(1, delivered().size());
        assertEquals("authorized", delivered().getFirst().getHeaders().get("simpSessionId"));
        assertTrue(new String((byte[]) delivered().getFirst().getPayload(), StandardCharsets.UTF_8).contains(image.attachmentUrl()));
    }

    @Test
    void forbiddenClientSendsCannotBroadcastOrPersist() {
        var principal = ChatChannelInterceptorTest.session("google");
        connect("authorized", principal);
        inbound.send(subscribe("authorized", principal, "/topic/rooms/1"));
        for (String destination : List.of("/topic/rooms/1", "/queue/test", "/user/queue/test", "/app/other/1",
                "/app/chat.send/2", "/app/chat.send/1/extra", "/app/chat.send/9223372036854775808")) {
            rejected(send("malicious", principal, destination));
        }
        verify(messages, never()).save(any());
        assertTrue(delivered().isEmpty());
    }

    @Test
    void deactivatedOrDeletedAccountsCannotConnectOrUseExistingSessionToSend() {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("session", principal);
        sender.setStatus("BANNED");
        rejected(send("session", principal, "/app/chat.send/1"));
        rejected(wire("CONNECT", "inactive", principal, "accept-version:1.2", ""));
        when(users.findByEmail("synthetic-chat@example.invalid")).thenReturn(Optional.empty());
        rejected(subscribe("session", principal, "/topic/rooms/1"));
        verify(messages, never()).save(any());
    }

    @Test
    void unsubscribeAndDisconnectRemoveBrokerSubscriptionsEvenAfterDeactivation() {
        var principal = ChatChannelInterceptorTest.session("local");
        connect("session", principal);
        inbound.send(subscribe("session", principal, "/topic/rooms/1"));
        sender.setStatus("BANNED");
        inbound.send(wire("UNSUBSCRIBE", "session", principal, "id:room-sub", ""));
        context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/rooms/1", (Object) Map.of("type", "TEXT"));
        assertTrue(delivered().isEmpty());
        sender.setStatus("ACTIVE");
        inbound.send(subscribe("session", principal, "/topic/rooms/1"));
        sender.setStatus("BANNED");
        inbound.send(wire("DISCONNECT", "session", principal, "", ""));
        context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/rooms/1", (Object) Map.of("type", "TEXT"));
        assertTrue(delivered().isEmpty());
    }

    @Test
    void sameOriginSockJsTransportAllowsBundledUiAndRejectsCrossOrigin() throws Exception {
        var mapping = context.getBean("stompWebSocketHandlerMapping", SimpleUrlHandlerMapping.class);
        var handler = (SockJsHttpRequestHandler) mapping.getUrlMap().get("/ws/**");
        assertNotNull(handler);
        assertEquals(200, originRequest(handler, "https://chat.example.invalid"));
        assertEquals(403, originRequest(handler, "https://external.example.invalid"));
    }

    private int originRequest(SockJsHttpRequestHandler handler, String origin) throws Exception {
        var request = new MockHttpServletRequest("GET", "/ws/info");
        request.setScheme("https");
        request.setServerName("chat.example.invalid");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        handler.getSockJsService().handleRequest(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), "/info", mock(org.springframework.web.socket.WebSocketHandler.class));
        return response.getStatus();
    }
}
