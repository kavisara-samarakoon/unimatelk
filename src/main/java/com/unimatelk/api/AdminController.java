package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ModerationCase;
import com.unimatelk.service.AdminService;
import com.unimatelk.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CurrentUserService current;
    private final AdminService adminService;

    public AdminController(CurrentUserService current, AdminService adminService) {
        this.current = current;
        this.adminService = adminService;
    }

    /** Day 12: Admin review dashboard (moderation cases) */
    @GetMapping("/cases")
    public java.util.Map<String, Object> cases(
            @AuthenticationPrincipal OAuth2User oauth,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AppUser admin = current.requireUser(oauth);
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;

        Page<ModerationCase> p = adminService.listCases(status, PageRequest.of(page, size));
        List<AdminDtos.ModerationCaseCard> items = p.getContent().stream().map(this::toCard).toList();
        return java.util.Map.of("items", items, "page", page, "size", size, "total", p.getTotalElements());
    }

    /** Day 12: Resolve case by UNBLOCK or BAN */
    @PostMapping("/cases/{caseId}/resolve")
    public AdminDtos.ModerationCaseCard resolve(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long caseId,
            @RequestBody AdminDtos.ResolveRequest req
    ) {
        AppUser admin = current.requireUser(oauth);
        ModerationCase c = adminService.resolve(admin, caseId, req != null ? req.action() : null, req != null ? req.note() : null);
        return toCard(c);
    }

    private AdminDtos.ModerationCaseCard toCard(ModerationCase c) {
        String resolvedBy = c.getResolvedBy() != null ? c.getResolvedBy().getEmail() : null;
        return new AdminDtos.ModerationCaseCard(
                c.getId(),
                c.getReportedUser().getId(),
                c.getReportedUser().getEmail(),
                c.getReportedUser().getName(),
                c.getReportedUser().getStatus(),
                c.getStatus(),
                c.getAction(),
                c.getResolutionNote(),
                c.getCreatedAt(),
                c.getResolvedAt(),
                resolvedBy
        );
    }
}
