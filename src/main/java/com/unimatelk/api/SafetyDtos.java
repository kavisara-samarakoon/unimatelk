package com.unimatelk.api;

import java.time.Instant;

public class SafetyDtos {

    public record ReportRequest(String reason) {}

    public record BlockCard(Long blockedUserId, String blockedName, Instant createdAt) {}
}
