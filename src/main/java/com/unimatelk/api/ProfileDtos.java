package com.unimatelk.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

public class ProfileDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyProfile(
            Long userId,
            String name,
            String campus,
            String faculty,
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpsertProfileRequest(
            @JsonAlias({"fullName"})
            String name,

            @JsonAlias({"university"})
            String campus,

            String faculty,
            String degree,

            @JsonAlias({"year"})
            Integer yearOfStudy,

            String gender,
            String genderPreference,
            String moveInMonth,

            String bio,
            String phone,
            String facebookUrl,
            String instagramUrl,

            Boolean contactVisible
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadResponse(String url, String path) {}
}