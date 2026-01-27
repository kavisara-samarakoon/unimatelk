package com.unimatelk.api;

import java.time.Instant;

public class ChatDtos {

    public record RoomCard(
            Long roomId,
            Long otherUserId,
            String otherName,
            String otherPicture,
            String lastMessage,
            Instant lastAt
    ) {}

    public record MessageDto(
            Long id,
            Long roomId,
            Long senderUserId,
            String senderName,
            String type,
            String content,
            String attachmentUrl,
            Instant createdAt
    ) {}

    public record SendMessage(String content) {}

    public record MessagePage(
            java.util.List<MessageDto> items,
            int page,
            int size,
            long total
    ) {}
}
