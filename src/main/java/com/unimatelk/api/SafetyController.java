package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Block;
import com.unimatelk.domain.Report;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.SafetyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    private final CurrentUserService current;
    private final SafetyService safety;

    public SafetyController(CurrentUserService current, SafetyService safety) {
        this.current = current;
        this.safety = safety;
    }

    /** Day 11: Block a user */
    @PostMapping("/block/{userId}")
    public SafetyDtos.BlockCard block(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long userId
    ) {
        AppUser me = current.requireUser(oauth);
        Block b = safety.block(me, userId);
        return new SafetyDtos.BlockCard(b.getBlocked().getId(), b.getBlocked().getName(), b.getCreatedAt());
    }

    /** Day 11: Unblock a user */
    @DeleteMapping("/block/{userId}")
    public Map<String, Object> unblock(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long userId
    ) {
        AppUser me = current.requireUser(oauth);
        safety.unblock(me, userId);
        return Map.of("ok", true);
    }

    /** Day 11: List my blocks */
    @GetMapping("/blocks")
    public List<SafetyDtos.BlockCard> blocks(@AuthenticationPrincipal OAuth2User oauth) {
        AppUser me = current.requireUser(oauth);
        return safety.myBlocks(me).stream()
                .map(b -> new SafetyDtos.BlockCard(b.getBlocked().getId(), b.getBlocked().getName(), b.getCreatedAt()))
                .toList();
    }

    /** Day 11: Report a user (unique reporter per reported) */
    @PostMapping("/report/{userId}")
    public Map<String, Object> report(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long userId,
            @RequestBody SafetyDtos.ReportRequest req
    ) {
        AppUser me = current.requireUser(oauth);
        Report r = safety.report(me, userId, req != null ? req.reason() : null);
        return Map.of("ok", true, "reportId", r.getId());
    }
}
