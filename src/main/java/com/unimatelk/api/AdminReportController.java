package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Report;
import com.unimatelk.domain.ReportStatus;
import com.unimatelk.repo.ReportRepository;
import com.unimatelk.service.AdminService;
import com.unimatelk.service.CurrentUserService;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final CurrentUserService currentUserService;
    private final AdminService adminService;
    private final ReportRepository reportRepo;

    public AdminReportController(CurrentUserService currentUserService,
                                 AdminService adminService,
                                 ReportRepository reportRepo) {
        this.currentUserService = currentUserService;
        this.adminService = adminService;
        this.reportRepo = reportRepo;
    }

    // ---------- DTOs ----------
    public static class ReportItem {
        public Long id;

        public Long reporterUserId;
        public String reporterEmail;

        public Long reportedUserId;
        public String reportedEmail;

        public String reason;
        public String details;

        public String status;

        public Instant createdAt;
        public Instant resolvedAt;
        public Long resolvedByUserId;
    }

    public static class PagedResponse<T> {
        public List<T> items;
        public int page;
        public int size;
        public long totalItems;
        public int totalPages;

        public PagedResponse(List<T> items, int page, int size, long totalItems, int totalPages) {
            this.items = items;
            this.page = page;
            this.size = size;
            this.totalItems = totalItems;
            this.totalPages = totalPages;
        }
    }

    public static class ResolveRequest {
        public String note;
    }

    // ---------- Admin guard ----------
    private AppUser requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Not logged in");
        }

        Object principal = auth.getPrincipal();

        AppUser me;

        // Case 1: OAuth2 login
        if (principal instanceof OAuth2User oauth2User) {
            me = currentUserService.requireUser(oauth2User);
        }
        // Case 2: local login (Spring Security UserDetails)
        else if (principal instanceof UserDetails userDetails) {
            // Your CurrentUserService likely has a way to get user by email/username.
            // We will treat username as email (common in your project).
            me = currentUserService.requireUserByEmail(userDetails.getUsername());
        }
        // Case 3: Sometimes principal is just a String (username)
        else if (principal instanceof String username) {
            me = currentUserService.requireUserByEmail(username);
        }
        else {
            throw new ResponseStatusException(UNAUTHORIZED, "Unsupported login type");
        }

        try {
            adminService.requireAdmin(me);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(FORBIDDEN, "Admin only");
        }

        return me;
    }

    // ---------- Endpoints ----------
    @GetMapping
    public ResponseEntity<PagedResponse<ReportItem>> list(
            Authentication auth,
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireAdmin(auth);

        ReportStatus st;
        try {
            st = ReportStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            st = ReportStatus.OPEN;
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Report> result;
        if (query != null && !query.trim().isEmpty()) {
            String q = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            result = reportRepo.searchByStatusFetched(st, q, pageable);
        } else {
            result = reportRepo.findByStatusFetched(st, pageable);
        }

        List<ReportItem> items = result.getContent().stream()
                .map(this::toItem)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        ));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(Authentication auth, @PathVariable Long id,
                                     @RequestBody(required = false) ResolveRequest body) {
        AppUser admin = requireAdmin(auth);

        Report report = reportRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Report not found"));

        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedAt(Instant.now());
        report.setResolvedByUserId(admin.getId());

        reportRepo.save(report);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private ReportItem toItem(Report r) {
        ReportItem it = new ReportItem();
        it.id = r.getId();

        if (r.getReporter() != null) {
            it.reporterUserId = r.getReporter().getId();
            it.reporterEmail = r.getReporter().getEmail();
        }
        if (r.getReported() != null) {
            it.reportedUserId = r.getReported().getId();
            it.reportedEmail = r.getReported().getEmail();
        }

        it.reason = r.getReason();
        it.details = r.getDetails();
        it.status = r.getStatus() != null ? r.getStatus().name() : null;

        it.createdAt = r.getCreatedAt();
        it.resolvedAt = r.getResolvedAt();
        it.resolvedByUserId = r.getResolvedByUserId();
        return it;
    }
}