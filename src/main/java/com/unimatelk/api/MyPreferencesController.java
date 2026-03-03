package com.unimatelk.api;

import com.unimatelk.domain.AppUser;
import com.unimatelk.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/my/prefs")
public class MyPreferencesController {

    private final CurrentUserService current;

    @PersistenceContext
    private EntityManager em;

    public MyPreferencesController(CurrentUserService current) {
        this.current = current;
    }

    /**
     * Ensure preferences row exists.
     * IMPORTANT: This is a write (INSERT), so it must run inside a transaction.
     */
    private void ensureRow(Long userId) {
        em.createNativeQuery("""
                INSERT INTO preferences (user_id)
                VALUES (?)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .setParameter(1, userId)
                .executeUpdate();
    }

    private static Integer i(Object o) { return o == null ? null : ((Number) o).intValue(); }

    private static Boolean bool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    public record PrefsDto(
            Integer sleepSchedule,
            Integer cleanliness,
            Integer noiseTolerance,
            Integer guests,
            Boolean smokingOk,
            Boolean drinkingOk,
            Integer introvert
    ) {}

    public record PrefsReq(
            Integer sleepSchedule,
            Integer cleanliness,
            Integer noiseTolerance,
            Integer guests,
            Boolean smokingOk,
            Boolean drinkingOk,
            Integer introvert
    ) {}

    @GetMapping
    @Transactional
    public PrefsDto get(@AuthenticationPrincipal OAuth2User oauth) {
        if (oauth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        AppUser me = current.requireUser(oauth);

        ensureRow(me.getId());

        List<?> rows = em.createNativeQuery("""
                SELECT sleep_schedule, cleanliness, noise_tolerance, guests, smoking_ok, drinking_ok, introvert
                FROM preferences
                WHERE user_id = ?
                LIMIT 1
                """)
                .setParameter(1, me.getId())
                .getResultList();

        if (rows.isEmpty()) return new PrefsDto(null, null, null, null, null, null, null);

        Object[] r = (Object[]) rows.get(0);
        return new PrefsDto(
                i(r[0]), i(r[1]), i(r[2]), i(r[3]),
                bool(r[4]), bool(r[5]),
                i(r[6])
        );
    }

    @PutMapping
    @Transactional
    public PrefsDto save(@AuthenticationPrincipal OAuth2User oauth, @RequestBody PrefsReq req) {
        if (oauth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        AppUser me = current.requireUser(oauth);

        ensureRow(me.getId());

        if (req.sleepSchedule != null) {
            em.createNativeQuery("UPDATE preferences SET sleep_schedule=? WHERE user_id=?")
                    .setParameter(1, req.sleepSchedule)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.cleanliness != null) {
            em.createNativeQuery("UPDATE preferences SET cleanliness=? WHERE user_id=?")
                    .setParameter(1, req.cleanliness)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.noiseTolerance != null) {
            em.createNativeQuery("UPDATE preferences SET noise_tolerance=? WHERE user_id=?")
                    .setParameter(1, req.noiseTolerance)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.guests != null) {
            em.createNativeQuery("UPDATE preferences SET guests=? WHERE user_id=?")
                    .setParameter(1, req.guests)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.smokingOk != null) {
            em.createNativeQuery("UPDATE preferences SET smoking_ok=? WHERE user_id=?")
                    .setParameter(1, req.smokingOk)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.drinkingOk != null) {
            em.createNativeQuery("UPDATE preferences SET drinking_ok=? WHERE user_id=?")
                    .setParameter(1, req.drinkingOk)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }
        if (req.introvert != null) {
            em.createNativeQuery("UPDATE preferences SET introvert=? WHERE user_id=?")
                    .setParameter(1, req.introvert)
                    .setParameter(2, me.getId())
                    .executeUpdate();
        }

        return get(oauth);
    }
}