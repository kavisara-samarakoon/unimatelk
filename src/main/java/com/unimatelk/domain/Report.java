package com.unimatelk.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reports_pair", columnNames = {"reporter_user_id", "reported_user_id"})
}, indexes = {
        @Index(name = "idx_reports_reported_created", columnList = "reported_user_id,created_at")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private AppUser reporter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private AppUser reported;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public AppUser getReporter() { return reporter; }
    public void setReporter(AppUser reporter) { this.reporter = reporter; }

    public AppUser getReported() { return reported; }
    public void setReported(AppUser reported) { this.reported = reported; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
