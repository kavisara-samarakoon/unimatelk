package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepo;
    private final AppProps props;

    public CurrentUserService(AppUserRepository userRepo, AppProps props) {
        this.userRepo = userRepo;
        this.props = props;
    }

    public AppUser requireUserByEmail(String email) {
        if (email == null || email.isBlank()) throw new RuntimeException("Unauthorized");
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
    }

    private boolean isAdminEmail(String email) {
        if (email == null) return false;
        return props.getAdminEmails().stream().anyMatch(e -> e.equalsIgnoreCase(email.trim()));
    }

    public AppUser requireUser(OAuth2User oauth) {
        if (oauth == null) throw new IllegalArgumentException("Not authenticated");

        String email = oauth.getAttribute("email");
        String name = oauth.getAttribute("name");
        String picture = oauth.getAttribute("picture");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google did not return an email.");
        }

        AppUser user = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setName(name != null ? name : "");
            u.setPictureUrl(picture);
            u.setRole(isAdminEmail(email) ? "ADMIN" : "STUDENT");
            u.setStatus("ACTIVE");
            u.setCreatedAt(Instant.now());
            u.setLastActiveAt(Instant.now());
            return userRepo.save(u);
        });

        // Update role on login too (if you add a new admin email later)
        if (isAdminEmail(email) && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            user.setRole("ADMIN");
        }

        user.setLastActiveAt(Instant.now());
        if (name != null) user.setName(name);
        if (picture != null) user.setPictureUrl(picture);
        userRepo.save(user);

        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is banned. Contact an admin.");
        }
        if ("TEMP_BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Your account is temporarily blocked. Contact an admin.");
        }

        return user;
    }
}