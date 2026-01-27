package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ChatMember;
import com.unimatelk.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    boolean existsByRoomAndUser(ChatRoom room, AppUser user);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatMember> findByRoom(ChatRoom room);

    @Query("""
            select cm.room.id from ChatMember cm
            where cm.user.id in (:u1, :u2)
            group by cm.room.id
            having count(cm.room.id) = 2
            """)
    List<Long> findCommonRoomIds(@Param("u1") Long user1Id, @Param("u2") Long user2Id);
}
