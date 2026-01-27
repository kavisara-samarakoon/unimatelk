package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_member", columnNames = {"room_id", "user_id"})
})
public class ChatMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    public Long getId() { return id; }

    public ChatRoom getRoom() { return room; }
    public void setRoom(ChatRoom room) { this.room = room; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
