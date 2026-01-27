package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.ProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final ProfileRepository repo;

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    public Profile getOrCreate(AppUser user) {
        return repo.findByUser(user).orElseGet(() -> {
            Profile p = new Profile();
            p.setUser(user);
            return repo.save(p);
        });
    }

    public Profile save(Profile p) { return repo.save(p); }
}
