package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/my/profile")
public class MyProfileController {

    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.uploads-dir:./uploads}")
    private String uploadsDir;

    public MyProfileController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    private AppUser requireMe(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof OAuth2User oauth2User)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not an OAuth session");
        }
        return currentUserService.requireUser(oauth2User);
    }

    // ✅ KEY FIX: always ensure a profile row exists (safe even if already exists)
    private void ensureProfileRow(Long userId) {
        em.createNativeQuery("INSERT IGNORE INTO profiles (user_id) VALUES (?)")
                .setParameter(1, userId)
                .executeUpdate();
    }

    private Object[] loadUserRow(Long userId) {
        List<?> rows = em.createNativeQuery("""
                SELECT name, picture_url
                FROM users
                WHERE id = ?
                LIMIT 1
                """)
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) return null;
        return (Object[]) rows.get(0);
    }

    private static String s(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer i(Object o) { return o == null ? null : ((Number) o).intValue(); }
    private static boolean b(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean bb) return bb;
        if (o instanceof Number nn) return nn.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    @GetMapping
    public ProfileDtos.MyProfile getMyProfile(Authentication auth) {
        AppUser me = requireMe(auth);
        Long userId = me.getId();

        // ensure profile row exists for ALL users
        ensureProfileRow(userId);

        Object[] urow = loadUserRow(userId);
        if (urow == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");

        String name = s(urow[0]);
        String userPicture = s(urow[1]);

        List<?> rows = em.createNativeQuery("""
                SELECT campus, faculty, degree, year_of_study, gender, gender_preference, move_in_month,
                       bio, profile_photo_path, cover_photo_path, contact_visible, phone, facebook_url, instagram_url
                FROM profiles
                WHERE user_id = ?
                LIMIT 1
                """)
                .setParameter(1, userId)
                .getResultList();

        Object[] p = (Object[]) rows.get(0);

        String campus = s(p[0]);
        String faculty = s(p[1]);
        String degree = s(p[2]);
        Integer yearOfStudy = i(p[3]);
        String gender = s(p[4]);
        String genderPreference = s(p[5]);
        String moveInMonth = s(p[6]);
        String bio = s(p[7]);
        String profilePhotoPath = s(p[8]);
        String coverPhotoPath = s(p[9]);
        boolean contactVisible = b(p[10]);
        String phone = s(p[11]);
        String facebookUrl = s(p[12]);
        String instagramUrl = s(p[13]);

        if (profilePhotoPath == null) profilePhotoPath = userPicture;

        return new ProfileDtos.MyProfile(
                userId, name,
                campus, faculty, degree, yearOfStudy,
                gender, genderPreference, moveInMonth,
                bio,
                profilePhotoPath,
                coverPhotoPath,
                contactVisible,
                phone,
                facebookUrl,
                instagramUrl
        );
    }

    @PutMapping
    @Transactional
    public ProfileDtos.MyProfile updateMyProfile(Authentication auth,
                                                 @RequestBody ProfileDtos.UpsertProfileRequest req) {
        AppUser me = requireMe(auth);
        Long userId = me.getId();

        ensureProfileRow(userId);

        if (req.name() != null && !req.name().isBlank()) {
            em.createNativeQuery("UPDATE users SET name = ? WHERE id = ?")
                    .setParameter(1, req.name().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.campus() != null) {
            em.createNativeQuery("UPDATE profiles SET campus = ? WHERE user_id = ?")
                    .setParameter(1, req.campus().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.faculty() != null) {
            em.createNativeQuery("UPDATE profiles SET faculty = ? WHERE user_id = ?")
                    .setParameter(1, req.faculty().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.degree() != null) {
            em.createNativeQuery("UPDATE profiles SET degree = ? WHERE user_id = ?")
                    .setParameter(1, req.degree().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.yearOfStudy() != null) {
            em.createNativeQuery("UPDATE profiles SET year_of_study = ? WHERE user_id = ?")
                    .setParameter(1, req.yearOfStudy())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.bio() != null) {
            em.createNativeQuery("UPDATE profiles SET bio = ? WHERE user_id = ?")
                    .setParameter(1, req.bio().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.phone() != null) {
            em.createNativeQuery("UPDATE profiles SET phone = ? WHERE user_id = ?")
                    .setParameter(1, req.phone().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.facebookUrl() != null) {
            em.createNativeQuery("UPDATE profiles SET facebook_url = ? WHERE user_id = ?")
                    .setParameter(1, req.facebookUrl().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.instagramUrl() != null) {
            em.createNativeQuery("UPDATE profiles SET instagram_url = ? WHERE user_id = ?")
                    .setParameter(1, req.instagramUrl().trim())
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        if (req.contactVisible() != null) {
            em.createNativeQuery("UPDATE profiles SET contact_visible = ? WHERE user_id = ?")
                    .setParameter(1, req.contactVisible() ? 1 : 0)
                    .setParameter(2, userId)
                    .executeUpdate();
        }

        // ✅ IMPORTANT: do not touch profile_photo_path here, so photo never vanishes.
        return getMyProfile(auth);
    }

    @PostMapping("/photo")
    @Transactional
    public ProfileDtos.UploadResponse uploadProfilePhoto(Authentication auth,
                                                         @RequestParam(required = false) MultipartFile file,
                                                         @RequestParam(required = false) MultipartFile image,
                                                         @RequestParam(required = false) MultipartFile photo) throws Exception {
        AppUser me = requireMe(auth);
        Long userId = me.getId();

        MultipartFile f = (file != null && !file.isEmpty()) ? file :
                (image != null && !image.isEmpty()) ? image :
                        (photo != null && !photo.isEmpty()) ? photo : null;

        if (f == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");

        Files.createDirectories(Paths.get(uploadsDir));

        String original = (f.getOriginalFilename() == null) ? "upload.jpg" : f.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);

        String filename = "profile-" + userId + "-" + Instant.now().toEpochMilli() + ext;
        Path target = Paths.get(uploadsDir).resolve(filename).normalize();
        Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String url = "/uploads/" + filename;

        ensureProfileRow(userId);

        em.createNativeQuery("UPDATE profiles SET profile_photo_path = ? WHERE user_id = ?")
                .setParameter(1, url)
                .setParameter(2, userId)
                .executeUpdate();

        // optional: keep navbar photo consistent
        em.createNativeQuery("UPDATE users SET picture_url = ? WHERE id = ?")
                .setParameter(1, url)
                .setParameter(2, userId)
                .executeUpdate();

        return new ProfileDtos.UploadResponse(url, filename);
    }
}