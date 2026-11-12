package com.unimatelk.service;

import com.unimatelk.api.ChatDtos;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatImageUploadService {
    private final CurrentUserService current;
    private final ChatService chat;
    private final StorageService storage;
    private final TransactionTemplate transaction;

    public ChatImageUploadService(CurrentUserService current, ChatService chat, StorageService storage,
            PlatformTransactionManager transactionManager) {
        this.current = current;
        this.chat = chat;
        this.storage = storage;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ChatDtos.MessageDto upload(OAuth2User principal, Long roomId, MultipartFile file) {
        CommittedMessage result = transaction.execute(status -> {
            var user = current.requireExistingActiveUser(principal);
            if (roomId == null || roomId <= 0) throw new IllegalArgumentException("Invalid room");
            var room = chat.getRoomOrThrow(roomId);
            chat.requireCanSend(user, room);
            String url = storage.storeChatImage(roomId, user.getId(), file);
            var message = chat.sendImage(user, room, url);
            // Response preparation is part of the transaction, not a post-save cleanup window.
            var prepared = new CommittedMessage(new ChatDtos.MessageDto(message.getId(), room.getId(),
                    user.getId(), user.getName(), message.getType(), message.getContent(),
                    message.getAttachmentUrl(), message.getCreatedAt()));
            TransactionSynchronizationManager.registerSynchronization(prepared);
            return prepared;
        });
        // Also reject an explicit rollback-only completion that did not throw an exception.
        if (result == null || !result.committed) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload was not committed");
        }
        return result.dto;
    }

    private static final class CommittedMessage implements TransactionSynchronization {
        private final ChatDtos.MessageDto dto;
        private boolean committed;

        CommittedMessage(ChatDtos.MessageDto dto) { this.dto = dto; }

        @Override
        public void afterCompletion(int status) { committed = status == STATUS_COMMITTED; }
    }
}
