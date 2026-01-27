package com.unimatelk.api;

public class ProfileDtos {
    public record PublicProfile(
            Long userId,
            String name,
            String campus,
            String degree,
            Integer yearOfStudy,
            String gender,
            String genderPreference,
            String moveInMonth,
            String bio,
            String profilePhotoPath,
            String coverPhotoPath,
            boolean contactVisible,
            String phone,
            String facebookUrl,
            String instagramUrl
    ) {}
}
