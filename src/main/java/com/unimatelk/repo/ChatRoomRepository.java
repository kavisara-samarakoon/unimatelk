package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
            select r from ChatRoom r
            join ChatMember m on m.room = r
            where m.user = :user
            order by r.id desc
            """
    )
    List<ChatRoom> findRoomsForUser(@Param("user") AppUser user);
}
