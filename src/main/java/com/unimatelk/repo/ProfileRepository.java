package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Optional<Profile> findByUser(AppUser user);

    Optional<Profile> findByUserId(Long userId);
}
