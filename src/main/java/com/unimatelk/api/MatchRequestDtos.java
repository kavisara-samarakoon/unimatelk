package com.unimatelk.api;

import java.time.Instant;

public class MatchRequestDtos {

    public record RequestCard(
            Long id,
            Long fromUserId,
            String fromName,
            String fromPicture,
            Long toUserId,
            String toName,
            String status,
            Instant createdAt
    ) {}
}
