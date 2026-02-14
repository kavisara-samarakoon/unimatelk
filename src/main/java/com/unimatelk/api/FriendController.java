package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.MatchRequest;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.MatchRequestRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class FriendController {

    private final AppUserRepository userRepo;
    private final MatchRequestRepository requestRepo;

    public FriendController(AppUserRepository userRepo, MatchRequestRepository requestRepo) {
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
    }

    private AppUser requireMe(OAuth2User oauth) {
        String email = oauth.getAttribute("email");
        if (email == null) throw new RuntimeException("No email in session");
        return userRepo.findByEmail(email).orElseThrow();
    }

    @GetMapping("/friends")
    public List<Map<String, Object>> friends(@AuthenticationPrincipal OAuth2User oauth) {
        AppUser me = requireMe(oauth);

        List<MatchRequest> accepted = requestRepo.findAcceptedForUser(me.getId());

        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchRequest r : accepted) {

            // ✅ These fields must match your MatchRequest entity.
            // If you used Version B in repository, replace getFromUser/getToUser accordingly.
            AppUser other = r.getFromUser().getId().equals(me.getId()) ? r.getToUser() : r.getFromUser();

            out.add(Map.of(
                    "id", other.getId(),
                    "name", other.getName(),
                    "email", other.getEmail(),
                    "picture", other.getPictureUrl(),
                    "role", other.getRole(),
                    "status", other.getStatus()
            ));
        }

        return out;
    }
}
