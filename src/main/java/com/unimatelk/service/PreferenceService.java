package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Preference;
import com.unimatelk.repo.PreferenceRepository;
import org.springframework.stereotype.Service;

@Service
public class PreferenceService {

    private final PreferenceRepository prefRepo;

    public PreferenceService(PreferenceRepository prefRepo) {
        this.prefRepo = prefRepo;
    }

    public Preference getOrCreate(AppUser user) {
        return prefRepo.findByUser(user).orElseGet(() -> {
            Preference p = new Preference();
            p.setUser(user);
            return prefRepo.save(p);
        });
    }

    public Preference save(Preference p) { return prefRepo.save(p); }
}