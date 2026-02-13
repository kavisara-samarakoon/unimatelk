package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
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
        if (user == null) return Map.of("authenticated", false);

        String email = user.getAttribute("email");
        String name = user.getAttribute("name");
        String picture = user.getAttribute("picture");

        AppUser dbUser = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setName(name != null ? name : "");
            u.setPictureUrl(picture);
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");
            u.setCreatedAt(Instant.now());
            u.setLastActiveAt(Instant.now());
            return userRepo.save(u);
        });

        dbUser.setLastActiveAt(Instant.now());
        userRepo.save(dbUser);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authenticated", true);
        out.put("id", dbUser.getId());
        out.put("userId", dbUser.getId());
        out.put("name", dbUser.getName());
        out.put("email", dbUser.getEmail());
        out.put("picture", dbUser.getPictureUrl());
        out.put("role", dbUser.getRole());
        out.put("status", dbUser.getStatus());
        return out;
    }
}
