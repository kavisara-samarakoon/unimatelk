package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ModerationCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModerationCaseRepository extends JpaRepository<ModerationCase, Long> {

    Optional<ModerationCase> findFirstByReportedUserAndStatusOrderByCreatedAtDesc(AppUser reportedUser, String status);

    Page<ModerationCase> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
