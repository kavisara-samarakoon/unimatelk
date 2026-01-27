package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Preference;
import com.unimatelk.service.PreferenceService;
import com.unimatelk.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
public class PreferenceController {
    private final CurrentUserService current;
    private final PreferenceService prefService;

    public PreferenceController(CurrentUserService current, PreferenceService prefService) {
        this.current = current;
        this.prefService = prefService;
    }

    @GetMapping("/api/preferences/me")
    public Preference me(@AuthenticationPrincipal OAuth2User oauth) {
        return prefService.getOrCreate(current.requireUser(oauth));
    }

    @PutMapping("/api/preferences/me")
    public Preference update(@AuthenticationPrincipal OAuth2User oauth,
                             @Valid @RequestBody PreferenceDtos.PreferenceUpdate req) {

        Preference p = prefService.getOrCreate(current.requireUser(oauth));

        // (You can later add more preference fields; matching service will use these values.)

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