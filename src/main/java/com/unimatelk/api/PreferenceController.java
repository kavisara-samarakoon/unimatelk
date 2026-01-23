package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Preference;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
public class PreferenceController {

    private final AppUserRepository userRepo;
    private final PreferenceService prefService;

    public PreferenceController(AppUserRepository userRepo, PreferenceService prefService) {
        this.userRepo = userRepo;
        this.prefService = prefService;
    }

    private AppUser currentUser(OAuth2User oauth) {
        return userRepo.findByEmail(oauth.getAttribute("email")).orElseThrow();
    }

    @GetMapping("/api/preferences/me")
    public Preference me(@AuthenticationPrincipal OAuth2User oauth) {
        return prefService.getOrCreate(currentUser(oauth));
    }

    @PutMapping("/api/preferences/me")
    public Preference update(@AuthenticationPrincipal OAuth2User oauth,
                             @Valid @RequestBody PreferenceDtos.PreferenceUpdate req) {

        Preference p = prefService.getOrCreate(currentUser(oauth));

        p.setSleepSchedule(req.sleepSchedule());
        p.setCleanliness(req.cleanliness());
        p.setNoiseTolerance(req.noiseTolerance());
        p.setGuests(req.guests());
        p.setSmokingOk(req.smokingOk());
        p.setDrinkingOk(req.drinkingOk());
        p.setIntrovert(req.introvert());

        return prefService.save(p);
    }
}