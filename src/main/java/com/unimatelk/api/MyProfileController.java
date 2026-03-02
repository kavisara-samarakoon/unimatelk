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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private void ensureProfileRow(Long userId) {
        em.createNativeQuery("""
                INSERT IGNORE INTO profiles
                (user_id, campus, degree, year_of_study, gender, gender_preference, contact_visible)
                VALUES (?, '', '', 1, '', '', 0)
                """)
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

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Profile row missing");
        }

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

        return getMyProfile(auth);
    }

    @PostMapping("/photo")
    @Transactional
    public Map<String, String> uploadProfilePhoto(Authentication auth,
                                                  @RequestParam(value = "file", required = false) MultipartFile file,
                                                  @RequestParam(value = "image", required = false) MultipartFile image,
                                                  @RequestParam(value = "photo", required = false) MultipartFile photo) {
        AppUser me = requireMe(auth);
        Long userId = me.getId();

        ensureProfileRow(userId);

        MultipartFile f = file != null ? file : (image != null ? image : photo);
        if (f == null || f.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }

        try {
            Path dir = Paths.get(uploadsDir);
            Files.createDirectories(dir);

            String original = f.getOriginalFilename() == null ? "photo" : f.getOriginalFilename();
            String ext = "";
            int idx = original.lastIndexOf('.');
            if (idx >= 0) ext = original.substring(idx).toLowerCase();
            if (!(ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp"))) {
                ext = ".png";
            }

            String filename = "profile_" + userId + "_" + UUID.randomUUID() + ext;
            Path target = dir.resolve(filename);

            Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String urlPath = "/uploads/" + filename;

            em.createNativeQuery("UPDATE profiles SET profile_photo_path = ? WHERE user_id = ?")
                    .setParameter(1, urlPath)
                    .setParameter(2, userId)
                    .executeUpdate();

            em.createNativeQuery("UPDATE users SET picture_url = ? WHERE id = ?")
                    .setParameter(1, urlPath)
                    .setParameter(2, userId)
                    .executeUpdate();

            return Map.of("url", urlPath);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed");
        }
    }
}