package com.unimatelk.api;

import java.util.List;

public class MatchDtos {

    public record MatchCard(
            Long userId,
            String name,
            String campus,
            String degree,
            Integer yearOfStudy,
            String gender,
            String profilePhotoPath,
            int matchPercent,
            List<String> whyMatched
    ) {}

    public record FeedResponse(
            List<MatchCard> items,
            int page,
            int size,
            long total
    ) {}

    public record ExplainResponse(
            Long userId,
            int matchPercent,
            List<String> whyMatched
    ) {}
}
