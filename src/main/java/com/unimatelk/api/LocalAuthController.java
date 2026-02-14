package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class LocalAuthController {

    private final AppUserRepository userRepo;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public LocalAuthController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // -------- DTOs --------
    public record SignupReq(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record LoginReq(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record ForgotReq(
            @NotBlank @Email String email
    ) {}

    public record ResetReq(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}

    // -------- Helpers --------
    private static String normEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void loginSession(HttpServletRequest request, AppUser u) {
        // Build a principal that looks like an OAuth2User so your existing controllers keep working
        List<GrantedAuthority> auths = List.of(new SimpleGrantedAuthority("ROLE_" + (u.getRole() == null ? "STUDENT" : u.getRole())));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", u.getEmail());
        attrs.put("name", u.getName());
        attrs.put("picture", u.getPictureUrl());

        DefaultOAuth2User principal = new DefaultOAuth2User(auths, attrs, "email");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, auths, "local");

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authentication);
        SecurityContextHolder.setContext(ctx);

        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    private String makeToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // -------- Endpoints --------

    @PostMapping("/signup")
    public Map<String, Object> signup(@Valid @RequestBody SignupReq req, HttpServletRequest request) {
        String email = normEmail(req.email());

        Optional<AppUser> existing = userRepo.findByEmail(email);
        if (existing.isPresent()) {
            AppUser u = existing.get();
            // If Google account exists, don't create duplicate
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This email is already registered. Please Sign In (Google or Reset Password).");
        }

        AppUser u = new AppUser();
        u.setEmail(email);
        u.setName(req.name().trim());
        u.setPictureUrl(null);
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        u.setCreatedAt(Instant.now());
        u.setLastActiveAt(Instant.now());

        u.setAuthProvider("LOCAL");
        u.setPasswordHash(encoder.encode(req.password()));

        userRepo.save(u);

        // ✅ auto-login after signup
        loginSession(request, u);

        return Map.of("ok", true);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        String email = normEmail(req.email());

        AppUser u = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This account uses Google login. Use Google or Reset Password to set a password.");
        }

        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if ("BANNED".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is banned.");
        }
        if ("TEMP_BLOCKED".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is temporarily blocked.");
        }

        u.setLastActiveAt(Instant.now());
        userRepo.save(u);

        loginSession(request, u);

        return Map.of("ok", true);
    }

    @PostMapping("/forgot")
    public Map<String, Object> forgot(@Valid @RequestBody ForgotReq req) {
        String email = normEmail(req.email());

        AppUser u = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account found for that email"));

        String token = makeToken();
        u.setResetToken(token);
        u.setResetTokenExpiresAt(Instant.now().plus(20, ChronoUnit.MINUTES));
        userRepo.save(u);

        // ✅ For student project: return reset link (no email sending required)
        String resetUrl = "/reset.html?token=" + token;

        return Map.of(
                "ok", true,
                "message", "Reset link generated",
                "resetUrl", resetUrl
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@Valid @RequestBody ResetReq req) {
        String token = req.token().trim();

        AppUser u = userRepo.findByResetToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token"));

        if (u.getResetTokenExpiresAt() == null || u.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token expired. Try again.");
        }

        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setAuthProvider("LOCAL"); // now user can login locally too
        u.setResetToken(null);
        u.setResetTokenExpiresAt(null);
        u.setLastActiveAt(Instant.now());
        userRepo.save(u);

        return Map.of("ok", true);
    }
}
