package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ChatRoom;
import com.unimatelk.domain.Message;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final AppUserRepository userRepo;
    private final SimpMessagingTemplate messaging;

    public ChatWsController(ChatService chatService, AppUserRepository userRepo, SimpMessagingTemplate messaging) {
        this.chatService = chatService;
        this.userRepo = userRepo;
        this.messaging = messaging;
    }

    /**
     * Day 9: Send text messages over WebSocket.
     * Client sends to: /app/chat.send/{roomId}
     */
    @MessageMapping("/chat.send/{roomId}")
    public void send(
            @DestinationVariable Long roomId,
            ChatDtos.SendMessage payload,
            Principal principal
    ) {
        AppUser sender = currentUserFromPrincipal(principal);
        ChatRoom room = chatService.getRoomOrThrow(roomId);
        Message msg = chatService.sendText(sender, room, payload != null ? payload.content() : null);

        ChatDtos.MessageDto dto = new ChatDtos.MessageDto(
                msg.getId(),
                roomId,
                msg.getSender().getId(),
                msg.getSender().getName(),
                msg.getType(),
                msg.getContent(),
                msg.getAttachmentUrl(),
                msg.getCreatedAt()
        );

        messaging.convertAndSend("/topic/rooms/" + roomId, dto);
    }

    private AppUser currentUserFromPrincipal(Principal principal) {
        if (principal == null) throw new IllegalArgumentException("Not authenticated");

        // In Spring Security + OAuth2 login, Principal is usually an Authentication
        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            return userRepo.findByEmail(email).orElseThrow();
        }
        throw new IllegalArgumentException("Unsupported principal");
    }
}
