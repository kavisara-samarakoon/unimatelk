package com.unimatelk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String campus = "";

    @Column(nullable = false, length = 200)
    private String degree = "";

    @Column(name = "year_of_study", nullable = false)
    private Integer yearOfStudy = 1;

    @Column(nullable = false, length = 20)
    private String gender = "";

    @Column(name = "gender_preference", nullable = false, length = 20)
    private String genderPreference = "";

    @Column(name = "move_in_month", length = 20)
    private String moveInMonth;

    @Column(length = 800)
    private String bio;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "profile_photo_path", length = 500)
    private String profilePhotoPath;

    @Column(name = "cover_photo_path", length = 500)
    private String coverPhotoPath;

    public Long getId() { return id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public Integer getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(Integer yearOfStudy) { this.yearOfStudy = yearOfStudy; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getGenderPreference() { return genderPreference; }
    public void setGenderPreference(String genderPreference) { this.genderPreference = genderPreference; }

    public String getMoveInMonth() { return moveInMonth; }
    public void setMoveInMonth(String moveInMonth) { this.moveInMonth = moveInMonth; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getFacebookUrl() { return facebookUrl; }
    public void setFacebookUrl(String facebookUrl) { this.facebookUrl = facebookUrl; }

    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String instagramUrl) { this.instagramUrl = instagramUrl; }

    public String getProfilePhotoPath() { return profilePhotoPath; }
    public void setProfilePhotoPath(String profilePhotoPath) { this.profilePhotoPath = profilePhotoPath; }

    public String getCoverPhotoPath() { return coverPhotoPath; }
    public void setCoverPhotoPath(String coverPhotoPath) { this.coverPhotoPath = coverPhotoPath; }
}
