package com.unimatelk.config;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepo;
    private final AppProps props;

    public CustomOAuth2UserService(AppUserRepository userRepo, AppProps props) {
        this.userRepo = userRepo;
        this.props = props;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");

        boolean isAdmin = props.getAdminEmails().stream()
                .anyMatch(a -> a.equalsIgnoreCase(email));

        // Create user if not exists
        AppUser user = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setName(name != null ? name : "");
            u.setPictureUrl(picture);
            u.setRole(isAdmin ? "ADMIN" : "STUDENT");
            u.setStatus("ACTIVE");
            u.setCreatedAt(Instant.now());
            return u;
        });

        // Update last active info each login
        user.setLastActiveAt(Instant.now());
        if (name != null) user.setName(name);
        if (picture != null) user.setPictureUrl(picture);

        // Keep role in sync with admin email list
        user.setRole(isAdmin ? "ADMIN" : "STUDENT");
        userRepo.save(user);

        // Map DB role to Spring Security ROLE_* so we can use hasRole(...) on endpoints
        Set<GrantedAuthority> authorities = new HashSet<>(oauthUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        // Use "email" as the name attribute key
        return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), "email");
    }
}
