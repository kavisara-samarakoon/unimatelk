package com.unimatelk.config;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.ChatService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private final ChatService chatService;
    private final AppUserRepository userRepo;

    public ChatChannelInterceptor(ChatService chatService, AppUserRepository userRepo) {
        this.chatService = chatService;
        this.userRepo = userRepo;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand cmd = accessor.getCommand();
        if (cmd == null) return message;

        // We only restrict SUBSCRIBE and SEND.
        if (cmd != StompCommand.SUBSCRIBE && cmd != StompCommand.SEND) return message;

        String destination = accessor.getDestination();
        if (destination == null) return message;

        Long roomId = extractRoomId(destination);
        if (roomId == null) return message;

        AppUser u = resolveUser(accessor.getUser());
        if (!"ACTIVE".equalsIgnoreCase(u.getStatus())) {
            throw new org.springframework.security.access.AccessDeniedException("User not active");
        }

        boolean member = chatService.isMember(roomId, u.getId());
        if (!member) {
            throw new org.springframework.security.access.AccessDeniedException("Not a member of room " + roomId);
        }

        return message;
    }

    private AppUser resolveUser(java.security.Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            return userRepo.findByEmail(email).orElseThrow();
        }
        throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
    }

    private Long extractRoomId(String destination) {
        // Allowed destinations:
        //  - /topic/rooms/{roomId}
        //  - /app/chat.send/{roomId}
        if (!(destination.startsWith("/topic/rooms/") || destination.startsWith("/app/chat.send/"))) {
            return null;
        }
        String[] parts = destination.split("/");
        if (parts.length == 0) return null;
        String last = parts[parts.length - 1];
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
