package com.unimatelk.api;

import java.time.Instant;

public class AdminReportDto {
    public Long id;
    public String reason;
    public String details;
    public String status;
    public Instant createdAt;

    public Long reporterUserId;
    public String reporterEmail;

    public Long reportedUserId;
    public String reportedEmail;

    public AdminReportDto() {}
}
