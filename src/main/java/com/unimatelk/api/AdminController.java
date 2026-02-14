package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppUserRepository userRepo;

    public AdminController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // ✅ This checks ADMIN from DB for EVERY admin API call
    private AppUser requireAdmin(OAuth2User oauth) {
        String email = oauth.getAttribute("email");
        if (email == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No email in session");

        AppUser me = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found in DB"));

        if (!"ADMIN".equalsIgnoreCase(me.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return me;
    }

    // ---------------- USERS ----------------
    @GetMapping("/users")
    public Map<String, Object> users(@org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User oauth,
                                     @RequestParam(defaultValue = "") String query) {
        requireAdmin(oauth);

        String q = query.trim().toLowerCase();
        List<AppUser> all = userRepo.findAll();

        var items = all.stream()
                .filter(u -> q.isBlank()
                        || (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                .map(u -> Map.of(
                        "id", u.getId(),
                        "name", u.getName(),
                        "email", u.getEmail(),
                        "role", u.getRole(),
                        "status", u.getStatus()
                ))
                .toList();

        return Map.of("items", items);
    }

    @PatchMapping("/users/{id}/role")
    public Map<String, Object> setRole(@org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User oauth,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        requireAdmin(oauth);

        String role = body.get("role");
        AppUser u = userRepo.findById(id).orElseThrow();
        u.setRole(role);
        userRepo.save(u);

        return Map.of("ok", true);
    }

    @PatchMapping("/users/{id}/status")
    public Map<String, Object> setStatus(@org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User oauth,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        requireAdmin(oauth);

        String status = body.get("status");
        AppUser u = userRepo.findById(id).orElseThrow();
        u.setStatus(status);
        userRepo.save(u);

        return Map.of("ok", true);
    }
}
