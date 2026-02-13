package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepo;

    public CurrentUserService(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public AppUser requireUser(OAuth2User oauth) {
        if (oauth == null) throw new IllegalArgumentException("Not authenticated");

        String email = oauth.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("No email returned from Google account");
        }

        // ✅ FIX: auto-create user if missing
        AppUser user = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);

            String name = oauth.getAttribute("name");
            String picture = oauth.getAttribute("picture");

            u.setName(name != null ? name : "");
            u.setPictureUrl(picture);
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");
            u.setCreatedAt(Instant.now());
            u.setLastActiveAt(Instant.now());
            return userRepo.save(u);
        });

        // update last active each time
        user.setLastActiveAt(Instant.now());
        userRepo.save(user);

        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is banned. Contact an admin.");
        }
        if ("TEMP_BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is temporarily blocked due to reports. Contact an admin.");
        }

        return user;
    }
}
