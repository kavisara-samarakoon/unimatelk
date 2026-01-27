package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "moderation_cases", indexes = {
        @Index(name = "idx_cases_status", columnList = "status")
})
public class ModerationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private AppUser reportedUser;

    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN / RESOLVED

    @Column(length = 30)
    private String action; // TEMP_BLOCK / UNBLOCK / BAN

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne
    @JoinColumn(name = "resolved_by_user_id")
    private AppUser resolvedBy;

    public Long getId() { return id; }

    public AppUser getReportedUser() { return reportedUser; }
    public void setReportedUser(AppUser reportedUser) { this.reportedUser = reportedUser; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public AppUser getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(AppUser resolvedBy) { this.resolvedBy = resolvedBy; }
}
