package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Message;
import com.unimatelk.service.ChatService;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.ChatImageUploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    private final CurrentUserService current;
    private final ChatService chatService;
    private final ChatImageUploadService uploads;
    private final SimpMessagingTemplate messaging;

    public ChatRestController(CurrentUserService current, ChatService chatService, ChatImageUploadService uploads, SimpMessagingTemplate messaging) {
        this.current = current;
        this.chatService = chatService;
        this.uploads = uploads;
        this.messaging = messaging;
    }

    /** Day 9: Inbox */
    @GetMapping("/rooms")
    public List<ChatDtos.RoomCard> rooms(@AuthenticationPrincipal OAuth2User oauth) {
        AppUser me = current.requireUser(oauth);
        return chatService.listRooms(me);
    }

    /** Day 9: History */
    @GetMapping("/rooms/{roomId}/messages")
    public ChatDtos.MessagePage messages(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        AppUser me = current.requireUser(oauth);
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 30;

        Page<Message> p = chatService.listMessages(me, roomId, PageRequest.of(page, size));
        List<ChatDtos.MessageDto> items = p.getContent().stream().map(this::toDto).toList();
        return new ChatDtos.MessagePage(items, page, size, p.getTotalElements());
    }

    /** Day 10: Upload image + broadcast as IMAGE message */
    @PostMapping("/rooms/{roomId}/image")
    public ChatDtos.MessageDto uploadImage(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file
    ) {
        // The service completes its transaction before returning; broadcast errors must retain the image.
        ChatDtos.MessageDto dto = uploads.upload(oauth, roomId, file);
        messaging.convertAndSend("/topic/rooms/" + roomId, dto);
        return dto;
    }

    private ChatDtos.MessageDto toDto(Message m) {
        return new ChatDtos.MessageDto(
                m.getId(),
                m.getRoom().getId(),
                m.getSender().getId(),
                m.getSender().getName(),
                m.getType(),
                m.getContent(),
                m.getAttachmentUrl(),
                m.getCreatedAt()
        );
    }
}
