package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.ProfileRepository;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.MatchingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
public class MatchingController {

    private final CurrentUserService current;
    private final MatchingService matching;
    private final ProfileRepository profileRepo;

    public MatchingController(CurrentUserService current, MatchingService matching, ProfileRepository profileRepo) {
        this.current = current;
        this.matching = matching;
        this.profileRepo = profileRepo;
    }

    /**
     * Day 6 + Day 7: Match feed with compatibility score, explanation, filters, pagination.
     */
    @GetMapping("/feed")
    public MatchDtos.FeedResponse feed(
            @AuthenticationPrincipal OAuth2User oauth,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genderPref,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AppUser me = current.requireUser(oauth);
        return matching.getFeed(me, campus, degree, year, genderPref, keyword, page, size);
    }

    /** Day 6: "Why matched" explanation for a specific user. */
    @GetMapping("/explain/{userId}")
    public MatchDtos.ExplainResponse explain(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long userId
    ) {
        AppUser me = current.requireUser(oauth);
        Profile other = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
        return matching.explain(me, other);
    }
}
