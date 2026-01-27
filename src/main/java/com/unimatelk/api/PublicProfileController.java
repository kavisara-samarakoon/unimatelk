package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.ProfileRepository;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.MatchRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class PublicProfileController {

    private final CurrentUserService current;
    private final ProfileRepository profileRepo;
    private final MatchRequestService matchRequestService;

    public PublicProfileController(CurrentUserService current, ProfileRepository profileRepo, MatchRequestService matchRequestService) {
        this.current = current;
        this.profileRepo = profileRepo;
        this.matchRequestService = matchRequestService;
    }

    /**
     * Day 8: View another user's profile.
     * Contact details are only visible if:
     *  - it's your own profile, OR
     *  - you are an ADMIN, OR
     *  - you have an ACCEPTED match request (chat unlocked)
     */
    @GetMapping("/{userId}")
    public ProfileDtos.PublicProfile get(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long userId
    ) {
        AppUser me = current.requireUser(oauth);
        Profile p = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        boolean isMe = me.getId().equals(userId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(me.getRole());
        boolean matched = matchRequestService.isMatched(me, p.getUser());

        boolean showContact = isMe || isAdmin || matched;

        return new ProfileDtos.PublicProfile(
                p.getUser().getId(),
                p.getUser().getName(),
                p.getCampus(),
                p.getDegree(),
                p.getYearOfStudy(),
                p.getGender(),
                p.getGenderPreference(),
                p.getMoveInMonth(),
                p.getBio(),
                p.getProfilePhotoPath(),
                p.getCoverPhotoPath(),
                showContact,
                showContact ? p.getPhone() : null,
                showContact ? p.getFacebookUrl() : null,
                showContact ? p.getInstagramUrl() : null
        );
    }
}
