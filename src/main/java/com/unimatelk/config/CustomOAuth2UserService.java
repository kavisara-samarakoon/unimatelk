package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepo;
    private final Set<String> adminEmails;

    public CustomOAuth2UserService(
            AppUserRepository userRepo,
            @Value("${app.admin-emails:}") String adminEmailsCsv
    ) {
        this.userRepo = userRepo;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase());
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth = super.loadUser(userRequest);

        String email = oauth.getAttribute("email");
        String name = oauth.getAttribute("name");
        String picture = oauth.getAttribute("picture");

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Google did not return an email.");
        }

        // ✅ load/create DB user
        AppUser u = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser nu = new AppUser();
            nu.setEmail(email);
            nu.setCreatedAt(Instant.now());
            return nu;
        });

        u.setName(name != null ? name : "");
        u.setPictureUrl(picture);
        u.setLastActiveAt(Instant.now());
        if (u.getStatus() == null) u.setStatus("ACTIVE");

        // ✅ ensure ADMIN role for your email (or keep DB role)
        String role = u.getRole();
        if (isAdminEmail(email)) role = "ADMIN";
        if (role == null || role.isBlank()) role = "STUDENT";
        u.setRole(role);

        userRepo.save(u);

        // ✅ IMPORTANT: add ROLE_ authority so hasRole("ADMIN") works
        Set<GrantedAuthority> authorities = new HashSet<>(oauth.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role)); // ROLE_ADMIN or ROLE_STUDENT

        // provider key name to use for username
        return new DefaultOAuth2User(authorities, oauth.getAttributes(), "email");
    }
}
