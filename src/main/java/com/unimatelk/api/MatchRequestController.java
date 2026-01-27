package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.MatchRequest;
import com.unimatelk.service.CurrentUserService;
import com.unimatelk.service.MatchRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class MatchRequestController {

    private final CurrentUserService current;
    private final MatchRequestService reqService;

    public MatchRequestController(CurrentUserService current, MatchRequestService reqService) {
        this.current = current;
        this.reqService = reqService;
    }

    @PostMapping("/{toUserId}")
    public MatchRequestDtos.RequestCard create(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long toUserId
    ) {
        AppUser me = current.requireUser(oauth);
        MatchRequest r = reqService.create(me, toUserId);
        return toCard(r);
    }

    @GetMapping("/incoming")
    public List<MatchRequestDtos.RequestCard> incoming(@AuthenticationPrincipal OAuth2User oauth) {
        AppUser me = current.requireUser(oauth);
        return reqService.incoming(me).stream().map(this::toCard).toList();
    }

    @GetMapping("/outgoing")
    public List<MatchRequestDtos.RequestCard> outgoing(@AuthenticationPrincipal OAuth2User oauth) {
        AppUser me = current.requireUser(oauth);
        return reqService.outgoing(me).stream().map(this::toCard).toList();
    }

    @PostMapping("/{requestId}/accept")
    public MatchRequestDtos.RequestCard accept(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long requestId
    ) {
        AppUser me = current.requireUser(oauth);
        return toCard(reqService.accept(me, requestId));
    }

    @PostMapping("/{requestId}/reject")
    public MatchRequestDtos.RequestCard reject(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long requestId
    ) {
        AppUser me = current.requireUser(oauth);
        return toCard(reqService.reject(me, requestId));
    }

    @DeleteMapping("/{requestId}")
    public MatchRequestDtos.RequestCard cancel(
            @AuthenticationPrincipal OAuth2User oauth,
            @PathVariable Long requestId
    ) {
        AppUser me = current.requireUser(oauth);
        return toCard(reqService.cancel(me, requestId));
    }

    private MatchRequestDtos.RequestCard toCard(MatchRequest r) {
        return new MatchRequestDtos.RequestCard(
                r.getId(),
                r.getFromUser().getId(),
                r.getFromUser().getName(),
                r.getFromUser().getPictureUrl(),
                r.getToUser().getId(),
                r.getToUser().getName(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
