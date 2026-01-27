package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "blocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_blocks_pair", columnNames = {"blocker_user_id", "blocked_user_id"})
})
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "blocker_user_id", nullable = false)
    private AppUser blocker;

    @ManyToOne(optional = false)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private AppUser blocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public AppUser getBlocker() { return blocker; }
    public void setBlocker(AppUser blocker) { this.blocker = blocker; }

    public AppUser getBlocked() { return blocked; }
    public void setBlocked(AppUser blocked) { this.blocked = blocked; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
