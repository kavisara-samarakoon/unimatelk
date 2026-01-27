package com.unimatelk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AppProps {
    @Value("${app.uploads-dir:./uploads}")
    private String uploadsDir;

    @Value("${app.admin-emails:}")
    private String adminEmailsRaw;

    @Value("${app.report-threshold:5}")
    private int reportThreshold;

    @Value("${app.report-window-days:7}")
    private int reportWindowDays;

    public String getUploadsDir() { return uploadsDir; }

    /** Comma-separated list in application.properties */
    public List<String> getAdminEmails() {
        if (adminEmailsRaw == null || adminEmailsRaw.isBlank()) return List.of();
        return Arrays.stream(adminEmailsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    public int getReportThreshold() { return reportThreshold; }

    public int getReportWindowDays() { return reportWindowDays; }
}
