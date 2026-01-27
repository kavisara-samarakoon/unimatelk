package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerAndBlocked(AppUser blocker, AppUser blocked);

    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker = :a and b.blocked = :b)
               or (b.blocker = :b and b.blocked = :a)
            """)
    boolean existsEitherDirection(@Param("a") AppUser a, @Param("b") AppUser b);

    @Query("select b.blocked.id from Block b where b.blocker = :u")
    List<Long> findBlockedIdsByBlocker(@Param("u") AppUser user);

    @Query("select b.blocker.id from Block b where b.blocked = :u")
    List<Long> findBlockerIdsWhoBlocked(@Param("u") AppUser user);

    void deleteByBlockerAndBlocked(AppUser blocker, AppUser blocked);
}
