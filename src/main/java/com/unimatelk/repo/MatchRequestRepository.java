package com.unimatelk.repo;

import com.unimatelk.domain.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // ✅ used by MatchRequestService.create()
    Optional<MatchRequest> findByFromUserAndToUser(com.unimatelk.domain.AppUser fromUser,
                                                   com.unimatelk.domain.AppUser toUser);

    // ✅ used for incoming requests list (receiver side)
    List<MatchRequest> findByToUserAndStatusOrderByCreatedAtDesc(com.unimatelk.domain.AppUser toUser, String status);

    // ✅ used for outgoing requests list (sender side)
    List<MatchRequest> findByFromUserAndStatusOrderByCreatedAtDesc(com.unimatelk.domain.AppUser fromUser, String status);

    // ✅ used for validation like "already requested"
    boolean existsByFromUserAndToUserAndStatus(com.unimatelk.domain.AppUser fromUser,
                                               com.unimatelk.domain.AppUser toUser,
                                               String status);

    // ✅ ✅ ✅ FIX: Friends list (all ACCEPTED where user is sender OR receiver)
    @Query("""
        select r from MatchRequest r
        join fetch r.fromUser
        join fetch r.toUser
        where r.status = 'ACCEPTED'
          and (r.fromUser.id = :uid or r.toUser.id = :uid)
        order by r.createdAt desc
    """)
    List<MatchRequest> findAcceptedForUser(@Param("uid") Long uid);
}
