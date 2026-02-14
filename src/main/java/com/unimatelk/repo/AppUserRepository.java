package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    // ✅ add this (used by admin + requireUser)
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
