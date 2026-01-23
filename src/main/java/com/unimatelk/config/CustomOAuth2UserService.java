package com.unimatelk.config;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepo;

    public CustomOAuth2UserService(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");

        // Create user if not exists
        AppUser user = userRepo.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setName(name != null ? name : "");
            u.setPictureUrl(picture);
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");
            u.setCreatedAt(Instant.now());
            return u;
        });

        // Update last active info each login
        user.setLastActiveAt(Instant.now());
        if (name != null) user.setName(name);
        if (picture != null) user.setPictureUrl(picture);

        userRepo.save(user);

        return oauthUser;
    }
}
