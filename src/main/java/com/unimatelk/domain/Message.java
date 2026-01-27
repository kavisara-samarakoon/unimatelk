package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_room_created", columnList = "room_id,created_at")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private AppUser sender;

    @Column(nullable = false, length = 20)
    private String type = "TEXT"; // TEXT / IMAGE / STICKER(optional)

    @Column(length = 2000)
    private String content;

    @Column(name = "attachment_url", length = 600)
    private String attachmentUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public ChatRoom getRoom() { return room; }
    public void setRoom(ChatRoom room) { this.room = room; }

    public AppUser getSender() { return sender; }
    public void setSender(AppUser sender) { this.sender = sender; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
