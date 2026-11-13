package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ChatRoom;
import com.unimatelk.domain.Message;
import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final CurrentUserService current;
    private final SimpMessagingTemplate messaging;

    public ChatWsController(ChatService chatService, CurrentUserService current, SimpMessagingTemplate messaging) {
        this.chatService = chatService;
        this.current = current;
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
        AppUser sender = current.requireExistingActiveSessionUser(principal);
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
}
