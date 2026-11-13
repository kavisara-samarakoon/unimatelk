package com.unimatelk.config;

import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ChatChannelInterceptor implements ExecutorChannelInterceptor {
    private static final Pattern ROOM_SUBSCRIPTION = Pattern.compile("/topic/rooms/([1-9][0-9]{0,18})");
    private static final Pattern TEXT_SEND = Pattern.compile("/app/chat\\.send/([1-9][0-9]{0,18})");

    private final ChatService chatService;
    private final CurrentUserService current;

    public ChatChannelInterceptor(ChatService chatService, CurrentUserService current) {
        this.chatService = chatService;
        this.current = current;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try {
            // STOMP wrapping rewrites native destinations and can hide duplicate headers.
            var accessor = SimpMessageHeaderAccessor.wrap(message);
            StompCommand command = StompHeaderAccessor.getCommand(message.getHeaders());
            if (command == null) {
                if (accessor.getMessageType() == SimpMessageType.HEARTBEAT) return message;
                throw denied();
            }
            switch (command) {
                case CONNECT, STOMP -> {
                    requireSession(accessor);
                    current.requireExistingActiveSessionUser(accessor.getUser());
                    requireSession(accessor);
                }
                // Cleanup must work even after account deactivation or a failed/anonymous connection.
                case DISCONNECT, UNSUBSCRIBE -> { return message; }
                case SUBSCRIBE, SEND -> {
                    String destination = accessor.getDestination();
                    if (destination == null) throw denied();
                    var nativeDestinations = accessor.getNativeHeader(StompHeaderAccessor.STOMP_DESTINATION_HEADER);
                    if (nativeDestinations != null && (nativeDestinations.size() != 1
                            || !destination.equals(nativeDestinations.getFirst()))) throw denied();
                    var pattern = command == StompCommand.SUBSCRIBE ? ROOM_SUBSCRIPTION : TEXT_SEND;
                    var matcher = pattern.matcher(destination);
                    if (!matcher.matches()) throw denied();
                    requireSession(accessor);
                    long roomId = Long.parseLong(matcher.group(1));
                    var user = current.requireExistingActiveSessionUser(accessor.getUser());
                    if (!chatService.isMember(roomId, user.getId())) throw denied();
                    requireSession(accessor);
                }
                // No client access to /queue, /user, arbitrary /app paths or server-only commands.
                default -> throw denied();
            }
            return message;
        } catch (RuntimeException rejected) {
            // Missing/malformed headers, overflow and lookup failures all fail closed with one public error.
            throw denied();
        }
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        try {
            StompCommand command = StompHeaderAccessor.getCommand(message.getHeaders());
            if (command == StompCommand.CONNECT || command == StompCommand.STOMP
                    || command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
                requireSession(SimpMessageHeaderAccessor.wrap(message));
            }
            return message;
        } catch (RuntimeException revoked) {
            return null; // Do not handle protected operations queued before logout.
        }
    }

    private static void requireSession(SimpMessageHeaderAccessor accessor) {
        if (!ChatHttpSessionBinding.active(accessor.getSessionAttributes(), accessor.getUser())) throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Chat operation denied");
    }
}
