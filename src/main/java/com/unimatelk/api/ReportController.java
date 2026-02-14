package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Report;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.SafetyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final AppUserRepository userRepo;
    private final SafetyService safetyService;

    public ReportController(AppUserRepository userRepo, SafetyService safetyService) {
        this.userRepo = userRepo;
        this.safetyService = safetyService;
    }

    private AppUser requireMe(OAuth2User oauth) {
        String email = oauth.getAttribute("email");
        if (email == null) throw new RuntimeException("No email in session");
        return userRepo.findByEmail(email).orElseThrow();
    }

    @PostMapping("/reports/{reportedUserId}")
    public Map<String, Object> reportUser(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long reportedUserId,
            @RequestBody Map<String, String> body
    ) {
        AppUser me = requireMe(oauth);

        String reason = body.getOrDefault("reason", "").trim();
        if (reason.isBlank()) throw new IllegalArgumentException("Reason is required");

        // SafetyService.report(reporter, reportedUserId, reason)
        Report r = safetyService.report(me, reportedUserId, reason);

        return Map.of("ok", true, "reportId", r.getId());
    }
}
