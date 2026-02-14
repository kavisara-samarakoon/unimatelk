package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    // ✅ FIX: needed for forgot-password / reset-password
    Optional<AppUser> findByResetToken(String resetToken);
}
