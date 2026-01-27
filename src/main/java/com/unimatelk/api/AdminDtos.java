package com.unimatelk.api;

import java.time.Instant;

public class AdminDtos {

    public record ModerationCaseCard(
            Long id,
            Long reportedUserId,
            String reportedEmail,
            String reportedName,
            String userStatus,
            String caseStatus,
            String action,
            String resolutionNote,
            Instant createdAt,
            Instant resolvedAt,
            String resolvedBy
    ) {}

    public record ResolveRequest(String action, String note) {}
}
