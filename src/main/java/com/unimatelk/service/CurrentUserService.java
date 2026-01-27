package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Small helper to convert OAuth2User (Google) -> our DB user.
 * Also enforces TEMP_BLOCKED/BANNED rules.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository userRepo;

    public CurrentUserService(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public AppUser requireUser(OAuth2User oauth) {
        if (oauth == null) throw new IllegalArgumentException("Not authenticated");
        String email = oauth.getAttribute("email");
        AppUser user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is banned. Contact an admin.");
        }
        if ("TEMP_BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is temporarily blocked due to reports. Contact an admin.");
        }

        return user;
    }
}
