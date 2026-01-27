package com.unimatelk.service;

import com.unimatelk.api.MatchDtos;
import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Preference;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.BlockRepository;
import com.unimatelk.repo.PreferenceRepository;
import com.unimatelk.repo.ProfileRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private final ProfileRepository profileRepo;
    private final PreferenceRepository prefRepo;
    private final BlockRepository blockRepo;

    public MatchingService(ProfileRepository profileRepo, PreferenceRepository prefRepo, BlockRepository blockRepo) {
        this.profileRepo = profileRepo;
        this.prefRepo = prefRepo;
        this.blockRepo = blockRepo;
    }

    public MatchDtos.FeedResponse getFeed(
            AppUser me,
            String campus,
            String degree,
            Integer year,
            String genderPref,
            String keyword,
            int page,
            int size
    ) {

        if (page < 0) page = 0;
        if (size <= 0 || size > 50) size = 10;

        // block filtering: users I blocked + users who blocked me
        Set<Long> blockedIds = new HashSet<>();
        blockedIds.addAll(blockRepo.findBlockedIdsByBlocker(me));
        blockedIds.addAll(blockRepo.findBlockerIdsWhoBlocked(me));

        Profile myProfile = profileRepo.findByUser(me).orElse(null);
        String defaultCampus = myProfile != null ? myProfile.getCampus() : null;
        String effectiveCampus = (campus != null && !campus.isBlank()) ? campus : defaultCampus;

        Preference myPref = prefRepo.findByUser(me).orElse(null);

        List<MatchDtos.MatchCard> all = profileRepo.findAll().stream()
                .filter(p -> p.getUser() != null)
                .filter(p -> !Objects.equals(p.getUser().getId(), me.getId()))
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getUser().getStatus()))
                .filter(p -> !blockedIds.contains(p.getUser().getId()))
                .filter(p -> effectiveCampus == null || effectiveCampus.isBlank() ||
                        p.getCampus() != null && p.getCampus().equalsIgnoreCase(effectiveCampus))
                .filter(p -> degree == null || degree.isBlank() ||
                        p.getDegree() != null && p.getDegree().toLowerCase().contains(degree.toLowerCase()))
                .filter(p -> year == null || Objects.equals(p.getYearOfStudy(), year))
                .filter(p -> genderCompatible(myProfile, p, genderPref))
                .filter(p -> keyword == null || keyword.isBlank() || matchesKeyword(p, keyword))
                .map(p -> toCard(p, myProfile, myPref))
                .sorted(Comparator.comparingInt(MatchDtos.MatchCard::matchPercent).reversed())
                .collect(Collectors.toList());

        long total = all.size();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<MatchDtos.MatchCard> items = all.subList(from, to);

        return new MatchDtos.FeedResponse(items, page, size, total);
    }

    public MatchDtos.ExplainResponse explain(AppUser me, Profile otherProfile) {
        Profile myProfile = profileRepo.findByUser(me).orElse(null);
        Preference myPref = prefRepo.findByUser(me).orElse(null);
        MatchResult r = computeScore(myPref, prefRepo.findByUser(otherProfile.getUser()).orElse(null));
        return new MatchDtos.ExplainResponse(otherProfile.getUser().getId(), r.percent, r.reasons);
    }

    private boolean matchesKeyword(Profile p, String keyword) {
        String k = keyword.toLowerCase();
        return (p.getDegree() != null && p.getDegree().toLowerCase().contains(k))
                || (p.getBio() != null && p.getBio().toLowerCase().contains(k))
                || (p.getCampus() != null && p.getCampus().toLowerCase().contains(k));
    }

    private boolean genderCompatible(Profile myProfile, Profile other, String overrideMyGenderPref) {
        // If profile isn't set yet, don't filter by gender.
        if (myProfile == null) return true;

        String myGender = myProfile.getGender();
        String myGenderPref = (overrideMyGenderPref != null && !overrideMyGenderPref.isBlank())
                ? overrideMyGenderPref
                : myProfile.getGenderPreference();

        // Rule:
        //  - other.gender must match my gender preference
        //  - my.gender must match other's gender preference
        boolean ok1 = prefers(myGenderPref, other.getGender());
        boolean ok2 = prefers(other.getGenderPreference(), myGender);
        return ok1 && ok2;
    }

    private boolean prefers(String genderPref, String candidateGender) {
        if (genderPref == null || genderPref.isBlank()) return true;
        String gp = genderPref.trim().toLowerCase();
        if (gp.equals("any") || gp.equals("all")) return true;
        if (candidateGender == null) return false;
        return candidateGender.trim().equalsIgnoreCase(genderPref.trim());
    }

    private MatchDtos.MatchCard toCard(Profile p, Profile myProfile, Preference myPref) {
        Preference otherPref = prefRepo.findByUser(p.getUser()).orElse(null);
        MatchResult r = computeScore(myPref, otherPref);
        return new MatchDtos.MatchCard(
                p.getUser().getId(),
                p.getUser().getName(),
                p.getCampus(),
                p.getDegree(),
                p.getYearOfStudy(),
                p.getGender(),
                p.getProfilePhotoPath(),
                r.percent,
                r.reasons
        );
    }

    private static class FeatureScore {
        String label;
        int points; // 0..weight
        String reason;
    }

    private static class MatchResult {
        int percent;
        List<String> reasons;
    }

    private MatchResult computeScore(Preference a, Preference b) {
        // Defaults when not filled yet
        int aSleep = safe1to5(a != null ? a.getSleepSchedule() : null);
        int bSleep = safe1to5(b != null ? b.getSleepSchedule() : null);

        int aClean = safe1to5(a != null ? a.getCleanliness() : null);
        int bClean = safe1to5(b != null ? b.getCleanliness() : null);

        int aNoise = safe1to5(a != null ? a.getNoiseTolerance() : null);
        int bNoise = safe1to5(b != null ? b.getNoiseTolerance() : null);

        int aGuests = safe1to5(a != null ? a.getGuests() : null);
        int bGuests = safe1to5(b != null ? b.getGuests() : null);

        Boolean aSmoke = a != null ? a.getSmokingOk() : null;
        Boolean bSmoke = b != null ? b.getSmokingOk() : null;

        Boolean aDrink = a != null ? a.getDrinkingOk() : null;
        Boolean bDrink = b != null ? b.getDrinkingOk() : null;

        int aIntro = safe1to5(a != null ? a.getIntrovert() : null);
        int bIntro = safe1to5(b != null ? b.getIntrovert() : null);

        List<FeatureScore> feats = new ArrayList<>();

        feats.add(scale("Sleep schedule", aSleep, bSleep, 20));
        feats.add(scale("Cleanliness", aClean, bClean, 20));
        feats.add(scale("Noise tolerance", aNoise, bNoise, 15));
        feats.add(scale("Guest comfort", aGuests, bGuests, 15));
        feats.add(boolMatch("Smoking", aSmoke, bSmoke, 10));
        feats.add(boolMatch("Drinking", aDrink, bDrink, 10));
        feats.add(scale("Personality", aIntro, bIntro, 10));

        int total = feats.stream().mapToInt(f -> f.points).sum();

        // choose top 3 reasons
        List<String> why = feats.stream()
                .sorted(Comparator.comparingInt((FeatureScore f) -> f.points).reversed())
                .limit(3)
                .map(f -> f.reason)
                .collect(Collectors.toList());

        MatchResult r = new MatchResult();
        r.percent = Math.max(0, Math.min(100, total));
        r.reasons = why;
        return r;
    }

    private FeatureScore scale(String label, int a, int b, int weight) {
        int diff = Math.abs(a - b); // 0..4
        double similarity = 1.0 - (diff / 4.0); // 1..0
        int points = (int) Math.round(similarity * weight);

        FeatureScore f = new FeatureScore();
        f.label = label;
        f.points = points;

        if (diff <= 1) f.reason = "✅ Similar " + label.toLowerCase();
        else if (diff == 2) f.reason = "👍 Compatible " + label.toLowerCase();
        else f.reason = "🙂 Some differences in " + label.toLowerCase();

        return f;
    }

    private FeatureScore boolMatch(String label, Boolean a, Boolean b, int weight) {
        // If any side missing, treat as neutral
        double similarity;
        if (a == null || b == null) similarity = 0.7;
        else similarity = Objects.equals(a, b) ? 1.0 : 0.0;

        int points = (int) Math.round(similarity * weight);
        FeatureScore f = new FeatureScore();
        f.label = label;
        f.points = points;
        if (similarity >= 1.0) f.reason = "✅ Same " + label.toLowerCase() + " preference";
        else if (similarity >= 0.7) f.reason = "👍 " + label + " preference not set (neutral)";
        else f.reason = "⚠️ Different " + label.toLowerCase() + " preference";
        return f;
    }

    private int safe1to5(Integer v) {
        if (v == null) return 3;
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }
}
