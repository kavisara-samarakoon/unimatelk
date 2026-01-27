package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "match_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_from_to", columnNames = {"from_user_id", "to_user_id"})
})
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private AppUser fromUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private AppUser toUser;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING / ACCEPTED / REJECTED / CANCELLED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }

    public AppUser getFromUser() { return fromUser; }
    public void setFromUser(AppUser fromUser) { this.fromUser = fromUser; }

    public AppUser getToUser() { return toUser; }
    public void setToUser(AppUser toUser) { this.toUser = toUser; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
