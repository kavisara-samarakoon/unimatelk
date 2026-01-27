package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AppUserRepository userRepo;

    public AuthController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return Map.of("authenticated", false);
        }

        String email = user.getAttribute("email");
        AppUser dbUser = userRepo.findByEmail(email).orElse(null);

        return Map.of(
                "authenticated", true,
                "id", dbUser != null ? dbUser.getId() : null,
                "name", user.getAttribute("name"),
                "email", email,
                "picture", user.getAttribute("picture"),
                "role", dbUser != null ? dbUser.getRole() : "STUDENT",
                "status", dbUser != null ? dbUser.getStatus() : "ACTIVE"
        );
    }
}
