package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.MatchRequest;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.BlockRepository;
import com.unimatelk.repo.MatchRequestRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MatchRequestService {

    private final AppUserRepository userRepo;
    private final MatchRequestRepository reqRepo;
    private final BlockRepository blockRepo;
    private final ChatService chatService;

    public MatchRequestService(AppUserRepository userRepo, MatchRequestRepository reqRepo, BlockRepository blockRepo, ChatService chatService) {
        this.userRepo = userRepo;
        this.reqRepo = reqRepo;
        this.blockRepo = blockRepo;
        this.chatService = chatService;
    }

    public MatchRequest create(AppUser from, Long toUserId) {
        if (toUserId == null) throw new IllegalArgumentException("User id is required");
        if (from.getId().equals(toUserId)) throw new IllegalArgumentException("You cannot match with yourself");

        AppUser to = userRepo.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"ACTIVE".equalsIgnoreCase(to.getStatus())) {
            throw new IllegalArgumentException("That user is not available");
        }
        if (blockRepo.existsEitherDirection(from, to)) {
            throw new IllegalArgumentException("You cannot match with this user (blocked)");
        }

        Optional<MatchRequest> existing = reqRepo.findByFromUserAndToUser(from, to);
        if (existing.isPresent()) {
            MatchRequest r = existing.get();
            if (!"CANCELLED".equalsIgnoreCase(r.getStatus()) && !"REJECTED".equalsIgnoreCase(r.getStatus())) {
                return r; // PENDING or ACCEPTED
            }
            // re-open if cancelled/rejected
            r.setStatus("PENDING");
            r.setUpdatedAt(Instant.now());
            return reqRepo.save(r);
        }

        MatchRequest r = new MatchRequest();
        r.setFromUser(from);
        r.setToUser(to);
        r.setStatus("PENDING");
        r.setCreatedAt(Instant.now());
        return reqRepo.save(r);
    }

    public List<MatchRequest> incoming(AppUser me) {
        return reqRepo.findByToUserAndStatusOrderByCreatedAtDesc(me, "PENDING");
    }

    public List<MatchRequest> outgoing(AppUser me) {
        return reqRepo.findByFromUserAndStatusOrderByCreatedAtDesc(me, "PENDING");
    }

    public MatchRequest accept(AppUser me, Long requestId) {
        MatchRequest r = reqRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!r.getToUser().getId().equals(me.getId())) {
            throw new IllegalArgumentException("Not your request");
        }
        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new IllegalArgumentException("Request is not pending");
        }
        if (blockRepo.existsEitherDirection(r.getFromUser(), r.getToUser())) {
            throw new IllegalArgumentException("Cannot accept (blocked)");
        }

        r.setStatus("ACCEPTED");
        r.setUpdatedAt(Instant.now());
        MatchRequest saved = reqRepo.save(r);

        // Create chat room on accept (mutual match gating)
        chatService.createRoomIfAbsent(r.getFromUser(), r.getToUser());

        return saved;
    }

    public MatchRequest reject(AppUser me, Long requestId) {
        MatchRequest r = reqRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!r.getToUser().getId().equals(me.getId())) {
            throw new IllegalArgumentException("Not your request");
        }
        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new IllegalArgumentException("Request is not pending");
        }

        r.setStatus("REJECTED");
        r.setUpdatedAt(Instant.now());
        return reqRepo.save(r);
    }

    public MatchRequest cancel(AppUser me, Long requestId) {
        MatchRequest r = reqRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!r.getFromUser().getId().equals(me.getId())) {
            throw new IllegalArgumentException("Not your request");
        }
        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new IllegalArgumentException("Request is not pending");
        }

        r.setStatus("CANCELLED");
        r.setUpdatedAt(Instant.now());
        return reqRepo.save(r);
    }

    public boolean isMatched(AppUser a, AppUser b) {
        return reqRepo.existsByFromUserAndToUserAndStatus(a, b, "ACCEPTED")
                || reqRepo.existsByFromUserAndToUserAndStatus(b, a, "ACCEPTED");
    }
}
