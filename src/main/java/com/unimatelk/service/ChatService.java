package com.unimatelk.service;

import com.unimatelk.api.ChatDtos;
import com.unimatelk.domain.*;
import com.unimatelk.repo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

    private final ChatRoomRepository roomRepo;
    private final ChatMemberRepository memberRepo;
    private final MessageRepository messageRepo;
    private final BlockRepository blockRepo;

    public ChatService(ChatRoomRepository roomRepo, ChatMemberRepository memberRepo, MessageRepository messageRepo, BlockRepository blockRepo) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
        this.messageRepo = messageRepo;
        this.blockRepo = blockRepo;
    }

    /** Called when a match request is accepted. */
    public ChatRoom createRoomIfAbsent(AppUser a, AppUser b) {
        if (blockRepo.existsEitherDirection(a, b)) {
            throw new IllegalArgumentException("Cannot chat (blocked)");
        }

        List<Long> common = memberRepo.findCommonRoomIds(a.getId(), b.getId());
        if (!common.isEmpty()) {
            return roomRepo.findById(common.get(0)).orElseThrow();
        }

        ChatRoom room = new ChatRoom();
        room.setCreatedAt(Instant.now());
        ChatRoom savedRoom = roomRepo.save(room);

        ChatMember m1 = new ChatMember();
        m1.setRoom(savedRoom);
        m1.setUser(a);
        m1.setJoinedAt(Instant.now());
        memberRepo.save(m1);

        ChatMember m2 = new ChatMember();
        m2.setRoom(savedRoom);
        m2.setUser(b);
        m2.setJoinedAt(Instant.now());
        memberRepo.save(m2);

        return savedRoom;
    }

    public boolean isMember(Long roomId, Long userId) {
        return memberRepo.existsByRoomIdAndUserId(roomId, userId);
    }

    public List<ChatDtos.RoomCard> listRooms(AppUser me) {
        return roomRepo.findRoomsForUser(me).stream()
                .map(r -> toRoomCard(me, r))
                .toList();
    }

    public Page<Message> listMessages(AppUser me, Long roomId, Pageable pageable) {
        if (!isMember(roomId, me.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not a member of this chat room");
        }
        return messageRepo.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }

    public Message sendText(AppUser sender, ChatRoom room, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message is empty");

        if (!memberRepo.existsByRoomAndUser(room, sender)) {
            throw new org.springframework.security.access.AccessDeniedException("Not a member of this chat room");
        }

        // block check between members
        List<ChatMember> members = memberRepo.findByRoom(room);
        Optional<AppUser> other = members.stream()
                .map(ChatMember::getUser)
                .filter(u -> !u.getId().equals(sender.getId()))
                .findFirst();
        other.ifPresent(o -> {
            if (blockRepo.existsEitherDirection(sender, o)) {
                throw new IllegalArgumentException("Cannot send message (blocked)");
            }
        });

        Message m = new Message();
        m.setRoom(room);
        m.setSender(sender);
        m.setType("TEXT");
        m.setContent(content.trim());
        m.setCreatedAt(Instant.now());
        return messageRepo.save(m);
    }

    public Message sendImage(AppUser sender, ChatRoom room, String attachmentUrl) {
        if (!memberRepo.existsByRoomAndUser(room, sender)) {
            throw new org.springframework.security.access.AccessDeniedException("Not a member of this chat room");
        }
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw new IllegalArgumentException("Attachment is empty");
        }

        Message m = new Message();
        m.setRoom(room);
        m.setSender(sender);
        m.setType("IMAGE");
        m.setAttachmentUrl(attachmentUrl);
        m.setCreatedAt(Instant.now());
        return messageRepo.save(m);
    }

    public ChatRoom getRoomOrThrow(Long roomId) {
        return roomRepo.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    }

    private ChatDtos.RoomCard toRoomCard(AppUser me, ChatRoom room) {
        List<ChatMember> members = memberRepo.findByRoom(room);
        AppUser other = members.stream()
                .map(ChatMember::getUser)
                .filter(u -> !u.getId().equals(me.getId()))
                .findFirst()
                .orElse(null);

        String otherName = other != null ? other.getName() : "Unknown";
        String otherPic = other != null ? other.getPictureUrl() : null;
        Long otherId = other != null ? other.getId() : null;

        var lastPage = messageRepo.findByRoomIdOrderByCreatedAtDesc(room.getId(), PageRequest.of(0, 1));
        String lastMsg = null;
        Instant lastAt = null;
        if (!lastPage.isEmpty()) {
            Message m = lastPage.getContent().get(0);
            lastAt = m.getCreatedAt();
            if ("IMAGE".equalsIgnoreCase(m.getType())) lastMsg = "[Image]";
            else lastMsg = m.getContent();
        }

        return new ChatDtos.RoomCard(room.getId(), otherId, otherName, otherPic, lastMsg, lastAt);
    }
}
