package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    Optional<MatchRequest> findByFromUserAndToUser(AppUser fromUser, AppUser toUser);

    List<MatchRequest> findByToUserAndStatusOrderByCreatedAtDesc(AppUser toUser, String status);

    List<MatchRequest> findByFromUserAndStatusOrderByCreatedAtDesc(AppUser fromUser, String status);

    boolean existsByFromUserAndToUserAndStatus(AppUser fromUser, AppUser toUser, String status);
}
