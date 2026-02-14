package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Report;
import com.unimatelk.domain.ReportStatus;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;   // ✅ IMPORTANT IMPORT
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminReportController {

    private final ReportRepository reportRepo;
    private final AppUserRepository userRepo;

    public AdminReportController(ReportRepository reportRepo, AppUserRepository userRepo) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReports(
            @AuthenticationPrincipal OAuth2User oauth,   // ✅ MUST be @AuthenticationPrincipal
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(required = false) String query
    ) {
        AppUser me = requireUser(oauth);
        requireAdmin(me);

        ReportStatus st;
        try {
            st = ReportStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }

        List<Report> reports = reportRepo.findByStatusWithUsers(st);

        String q = (query == null) ? "" : query.trim().toLowerCase();
        if (!q.isEmpty()) {
            reports.removeIf(r -> {
                String reporterEmail = (r.getReporter() == null || r.getReporter().getEmail() == null) ? "" : r.getReporter().getEmail().toLowerCase();
                String reportedEmail = (r.getReported() == null || r.getReported().getEmail() == null) ? "" : r.getReported().getEmail().toLowerCase();
                String reason = (r.getReason() == null) ? "" : r.getReason().toLowerCase();
                String details = (r.getDetails() == null) ? "" : r.getDetails().toLowerCase();
                return !(reporterEmail.contains(q) || reportedEmail.contains(q) || reason.contains(q) || details.contains(q));
            });
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Report r : reports) out.add(toDto(r));
        return out;
    }

    private AppUser requireUser(OAuth2User oauth) {
        if (oauth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");

        String email = (String) oauth.getAttributes().get("email");
        if (email == null || email.isBlank()) {
            Object alt = oauth.getAttributes().get("preferred_username");
            email = (alt == null) ? null : alt.toString();
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not found in OAuth2User");
        }

        final String finalEmail = email;

        return userRepo.findByEmailIgnoreCase(finalEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found in DB for: " + finalEmail
                ));
    }

    private void requireAdmin(AppUser me) {
        String role = (me.getRole() == null) ? "" : me.getRole().toString();
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    private Map<String, Object> toDto(Report r) {
        Map<String, Object> m = new LinkedHashMap<>();

        m.put("id", r.getId());

        m.put("reporterUserId", r.getReporter() == null ? null : r.getReporter().getId());
        m.put("reporterEmail", r.getReporter() == null ? null : r.getReporter().getEmail());

        m.put("reportedUserId", r.getReported() == null ? null : r.getReported().getId());
        m.put("reportedEmail", r.getReported() == null ? null : r.getReported().getEmail());

        m.put("reason", r.getReason());
        m.put("details", r.getDetails());

        m.put("status", r.getStatus() == null ? "OPEN" : r.getStatus().toString());
        m.put("createdAt", r.getCreatedAt());
        m.put("resolvedAt", r.getResolvedAt());
        m.put("resolvedByUserId", r.getResolvedByUserId());

        return m;
    }
}
