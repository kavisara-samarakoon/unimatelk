package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Preference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {
    Optional<Preference> findByUser(AppUser user);
}
