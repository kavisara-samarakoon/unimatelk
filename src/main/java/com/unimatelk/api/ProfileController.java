package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import com.unimatelk.service.ProfileService;
import com.unimatelk.service.StorageService;
import com.unimatelk.service.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RestController
public class ProfileController {
    private final CurrentUserService current;
    private final ProfileService profileService;
    private final StorageService storageService;

    public ProfileController(CurrentUserService current, ProfileService profileService, StorageService storageService) {
        this.current = current;
        this.profileService = profileService;
        this.storageService = storageService;
    }

    @GetMapping("/api/profile/me")
    public Profile me(@AuthenticationPrincipal OAuth2User oauth) {
        return profileService.getOrCreate(current.requireUser(oauth));
    }

    @PutMapping("/api/profile/me")
    public Profile updateMe(@AuthenticationPrincipal OAuth2User oauth, @RequestBody Profile incoming) {
        Profile p = profileService.getOrCreate(current.requireUser(oauth));

        p.setCampus(incoming.getCampus() == null ? "" : incoming.getCampus());
        p.setDegree(incoming.getDegree() == null ? "" : incoming.getDegree());
        p.setYearOfStudy(incoming.getYearOfStudy() == null ? 1 : incoming.getYearOfStudy());
        p.setGender(incoming.getGender() == null ? "" : incoming.getGender());
        p.setGenderPreference(incoming.getGenderPreference() == null ? "" : incoming.getGenderPreference());

        p.setMoveInMonth(incoming.getMoveInMonth());
        p.setBio(incoming.getBio());
        p.setPhone(incoming.getPhone());
        p.setFacebookUrl(incoming.getFacebookUrl());
        p.setInstagramUrl(incoming.getInstagramUrl());

        return profileService.save(p);
    }

    @PostMapping("/api/profile/me/profile-photo")
    @Transactional
    public Map<String, Object> uploadProfile(@AuthenticationPrincipal OAuth2User oauth,
                                             @RequestParam("file") MultipartFile file) {
        AppUser u = current.requireExistingActiveUser(oauth);
        String url = storageService.storeProfileImage(u.getId(), file, "profile");
        Profile p = profileService.getOrCreate(u);
        p.setProfilePhotoPath(url);
        profileService.save(p);

        return Map.of("url", url);
    }

    @PostMapping("/api/profile/me/cover-photo")
    @Transactional
    public Map<String, Object> uploadCover(@AuthenticationPrincipal OAuth2User oauth,
                                           @RequestParam("file") MultipartFile file) {
        AppUser u = current.requireExistingActiveUser(oauth);
        String url = storageService.storeProfileImage(u.getId(), file, "cover");
        Profile p = profileService.getOrCreate(u);
        p.setCoverPhotoPath(url);
        profileService.save(p);

        return Map.of("url", url);
    }
}
