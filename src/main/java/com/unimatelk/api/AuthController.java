package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AuthController {

    private final AppUserRepository userRepo;

    public AuthController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        // Not logged in
        if (user == null) {
            return Map.of("authenticated", false);
        }

        String email = user.getAttribute("email");
        String name = user.getAttribute("name");
        String picture = user.getAttribute("picture");

        AppUser dbUser = null;
        if (email != null && !email.isBlank()) {
            dbUser = userRepo.findByEmail(email).orElse(null);
        }

        // Use a normal Map (allows null values). Map.of() DOES NOT allow nulls.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authenticated", true);

        Long id = (dbUser != null ? dbUser.getId() : null);
        out.put("id", id);

        // For your chat.js (it expects userId)
        out.put("userId", id);

        out.put("name", name != null ? name : "");
        out.put("email", email != null ? email : "");
        out.put("picture", picture); // can be null, OK in LinkedHashMap

        out.put("role", (dbUser != null && dbUser.getRole() != null) ? dbUser.getRole() : "STUDENT");
        out.put("status", (dbUser != null && dbUser.getStatus() != null) ? dbUser.getStatus() : "ACTIVE");

        return out;
    }
}
