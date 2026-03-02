package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final CurrentUserService current;

    @PersistenceContext
    private EntityManager em;

    public UsersController(CurrentUserService current) {
        this.current = current;
    }

    public record UserPublicDto(
            Long id,
            String name,
            String pictureUrl,
            String campus,
            String faculty,
            String degree,
            Integer yearOfStudy,
            String bio
    ) {}

    private static String s(Object o) { return o == null ? null : String.valueOf(o); }
    private static Integer i(Object o) { return o == null ? null : ((Number) o).intValue(); }

    @GetMapping("/{id}")
    public UserPublicDto getUser(@AuthenticationPrincipal OAuth2User oauth, @PathVariable Long id) {
        if (oauth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        current.requireUser(oauth);

        List<?> urows = em.createNativeQuery("""
                SELECT id, name, picture_url
                FROM users
                WHERE id = ?
                LIMIT 1
                """)
                .setParameter(1, id)
                .getResultList();

        if (urows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Object[] u = (Object[]) urows.get(0);
        Long userId = ((Number) u[0]).longValue();
        String name = s(u[1]);
        String pictureUrl = s(u[2]);

        List<?> prows = em.createNativeQuery("""
                SELECT campus, faculty, degree, year_of_study, bio
                FROM profiles
                WHERE user_id = ?
                LIMIT 1
                """)
                .setParameter(1, userId)
                .getResultList();

        String campus = null, faculty = null, degree = null, bio = null;
        Integer year = null;

        if (!prows.isEmpty()) {
            Object[] p = (Object[]) prows.get(0);
            campus = s(p[0]);
            faculty = s(p[1]);
            degree = s(p[2]);
            year = i(p[3]);
            bio = s(p[4]);
        }

        return new UserPublicDto(userId, name, pictureUrl, campus, faculty, degree, year, bio);
    }
}