package com.unimatelk.repo;

import com.unimatelk.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    List<Message> findByRoomIdAndTypeAndAttachmentUrl(Long roomId, String type, String attachmentUrl);
}
