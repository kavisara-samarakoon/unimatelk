package com.unimatelk.config;

import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import jakarta.annotation.PreDestroy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/** Authorize each recipient at transport handling time, including messages already queued for delivery. */
@Component
public class ChatOutboundAuthorization implements ExecutorChannelInterceptor, WebSocketHandlerDecoratorFactory {
    private static final Pattern ROOM = Pattern.compile("/topic/rooms/([1-9][0-9]{0,18})");
    // One reference per open transport, shared across its subscriptions. Never cache permissions.
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final CurrentUserService current;
    private final ChatService chat;

    public ChatOutboundAuthorization(CurrentUserService current, ChatService chat) {
        this.current = current;
        this.chat = chat;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        try {
            var headers = message.getHeaders();
            var type = SimpMessageHeaderAccessor.getMessageType(headers);
            if (type == null) return null;
            if (type != SimpMessageType.MESSAGE) return message; // CONNECTED, receipts, heartbeats, cleanup.
            String destination = SimpMessageHeaderAccessor.getDestination(headers);
            if (destination == null) return null;
            var room = ROOM.matcher(destination);
            if (!room.matches()) return null;
            long roomId = Long.parseLong(room.group(1));
            String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
            var session = sessionId == null ? null : sessions.get(sessionId);
            if (session == null || !session.isOpen()) return null;
            if (!ChatHttpSessionBinding.active(session.getAttributes(), session.getPrincipal())) return null;
            // Broker MESSAGE headers may contain the sender's principal, never trust them as the recipient.
            var user = current.requireExistingActiveSessionUser(session.getPrincipal());
            if (!chat.isMember(roomId, user.getId())) return null;
            return sessions.get(sessionId) == session && session.isOpen()
                    && ChatHttpSessionBinding.active(session.getAttributes(), session.getPrincipal()) ? message : null;
        } catch (RuntimeException denied) {
            // Silent drop: throwing from this channel can cause the broker to log the private payload.
            return null;
        }
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                var binding = ChatHttpSessionBinding.from(session.getAttributes());
                if (binding == null || !binding.active(session.getPrincipal())) {
                    closeRevoked(session);
                    throw new IllegalStateException("Chat connection denied");
                }
                if (sessions.putIfAbsent(session.getId(), session) != null) {
                    throw new IllegalStateException("Chat connection denied");
                }
                try {
                    if (!binding.register(session, () -> closeRevoked(session)) || !binding.active(session.getPrincipal())) {
                        throw new IllegalStateException("Chat connection denied");
                    }
                    // This gate is inside Spring's transport write buffer, checking again at the final write.
                    var guarded = new WebSocketSessionDecorator(session) {
                        @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
                            if (sessions.get(session.getId()) == session && session.isOpen()
                                    && binding.active(getPrincipal())) super.sendMessage(message);
                        }
                    };
                    super.afterConnectionEstablished(guarded);
                    if (!binding.active(session.getPrincipal())) {
                        super.afterConnectionClosed(session, CloseStatus.POLICY_VIOLATION);
                        throw new IllegalStateException("Chat connection denied");
                    }
                } catch (Exception | Error failed) {
                    sessions.remove(session.getId(), session);
                    binding.unregister(session);
                    closeRevoked(session);
                    throw failed;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId(), session);
                unregister(session);
                super.afterConnectionClosed(session, status);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                sessions.remove(session.getId(), session);
                unregister(session);
                super.handleTransportError(session, exception);
            }
        };
    }

    private void closeRevoked(WebSocketSession session) {
        sessions.remove(session.getId(), session);
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException | RuntimeException failed) {
            if (failed instanceof InterruptedIOException) Thread.currentThread().interrupt();
            // Binding and channel guards remain revoked even if transport termination fails.
        }
    }

    private void unregister(WebSocketSession session) {
        var binding = ChatHttpSessionBinding.from(session.getAttributes());
        if (binding != null) binding.unregister(session);
    }

    @PreDestroy
    void clearSessions() {
        sessions.values().forEach(this::unregister);
        sessions.clear();
    }
}
