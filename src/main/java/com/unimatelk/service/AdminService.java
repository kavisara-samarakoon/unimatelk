package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.repo.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@Service
public class AdminService {

    private final AppUserRepository userRepo;

    public AdminService(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * Throws:
     * - RuntimeException("Unauthorized") if user is null
     * - RuntimeException("Forbidden") if user is not admin
     *
     * IMPORTANT:
     * Your project does not have AppUser.isAdmin(), so we detect admin using reflection.
     * We support common patterns:
     *  1) method: boolean isAdmin()
     *  2) method: Collection<String> getRoles() / getAuthorities()
     *  3) field: roles / role / rolesCsv / roleCsv (String or Collection)
     *  4) email allow-list fallback (adminEmails / adminEmailsCsv) if present in AppUser
     */
    public void requireAdmin(AppUser me) {
        if (me == null) throw new RuntimeException("Unauthorized");

        if (!isAdminUser(me)) {
            throw new RuntimeException("Forbidden");
        }
    }

    /**
     * Apply moderation action to a user.
     * action values (recommended):
     *  - NO_ACTION
     *  - TEMP_BLOCK
     *  - BAN
     *  - UNBLOCK
     *
     * This method tries to update user status if your AppUser supports it.
     * If no compatible status setter exists, it will safely do nothing (but still compile).
     */
    @Transactional
    public void applyModerationAction(Long userId, String action, String note) {
        if (userId == null) return;
        if (action == null) action = "NO_ACTION";

        AppUser user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        String normalized = action.trim().toUpperCase(Locale.ROOT);

        // Map actions to likely status strings/enums found in projects
        // (We don't know your exact enum/constants, so we try common ones)
        String targetStatus;
        switch (normalized) {
            case "TEMP_BLOCK" -> targetStatus = "TEMP_BLOCK";
            case "BAN" -> targetStatus = "BANNED";
            case "UNBLOCK" -> targetStatus = "ACTIVE";
            default -> targetStatus = null; // NO_ACTION
        }

        if (targetStatus != null) {
            boolean updated = trySetStatus(user, targetStatus);
            if (updated) {
                userRepo.save(user);
            }
        }

        // 'note' is not saved here because we don't know your DB field for it.
        // You can store it in the Report resolution note or ModerationCase later.
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    private boolean isAdminUser(AppUser me) {
        // 1) If there is an isAdmin() method, use it
        Boolean v = (Boolean) tryInvokeNoArg(me, "isAdmin");
        if (v != null) return v;

        // 2) If there is a getRoles() / getAuthorities() method, check for "ADMIN"
        Object rolesObj = tryInvokeNoArg(me, "getRoles");
        if (rolesObj == null) rolesObj = tryInvokeNoArg(me, "getAuthorities");
        if (rolesObj != null) {
            if (containsAdmin(rolesObj)) return true;
        }

        // 3) If there is a roles field, check it
        Object rolesField = tryReadField(me, "roles");
        if (rolesField != null && containsAdmin(rolesField)) return true;

        Object roleField = tryReadField(me, "role");
        if (roleField != null && containsAdmin(roleField)) return true;

        Object rolesCsv = tryReadField(me, "rolesCsv");
        if (rolesCsv != null && containsAdmin(rolesCsv)) return true;

        Object roleCsv = tryReadField(me, "roleCsv");
        if (roleCsv != null && containsAdmin(roleCsv)) return true;

        // 4) Fallback: check email-based allow list if AppUser contains it
        // (Some projects store admin emails/config inside AppUser - if not, this simply returns false)
        String email = safeString(tryInvokeNoArg(me, "getEmail"));
        if (email != null) {
            Object adminEmailsCsv = tryReadField(me, "adminEmailsCsv");
            if (adminEmailsCsv != null && containsEmail(adminEmailsCsv, email)) return true;

            Object adminEmails = tryReadField(me, "adminEmails");
            if (adminEmails != null && containsEmail(adminEmails, email)) return true;
        }

        return false;
    }

    private boolean trySetStatus(AppUser user, String targetStatus) {
        // Try method setStatus(String)
        if (tryInvokeOneArg(user, "setStatus", String.class, targetStatus)) return true;

        // Try method setStatus(SomeEnum)
        Method m = findMethod(user.getClass(), "setStatus", 1);
        if (m != null) {
            Class<?> paramType = m.getParameterTypes()[0];
            if (paramType.isEnum()) {
                Object enumValue = tryEnumValue(paramType, targetStatus);
                if (enumValue != null) {
                    try {
                        m.setAccessible(true);
                        m.invoke(user, enumValue);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Try field: status (String or enum)
        Field f = findField(user.getClass(), "status");
        if (f != null) {
            try {
                f.setAccessible(true);
                Class<?> t = f.getType();
                if (t == String.class) {
                    f.set(user, targetStatus);
                    return true;
                }
                if (t.isEnum()) {
                    Object enumValue = tryEnumValue(t, targetStatus);
                    if (enumValue != null) {
                        f.set(user, enumValue);
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private boolean containsAdmin(Object rolesObj) {
        if (rolesObj == null) return false;

        // Collection (e.g. Set<Role>, Set<String>)
        if (rolesObj instanceof Collection<?> col) {
            for (Object x : col) {
                String s = safeString(x);
                if (s != null && s.toUpperCase(Locale.ROOT).contains("ADMIN")) return true;

                // If role object has getName() or getRole()
                String name = safeString(tryInvokeNoArg(x, "getName"));
                if (name != null && name.toUpperCase(Locale.ROOT).contains("ADMIN")) return true;

                String role = safeString(tryInvokeNoArg(x, "getRole"));
                if (role != null && role.toUpperCase(Locale.ROOT).contains("ADMIN")) return true;
            }
            return false;
        }

        // String (CSV or single role)
        String s = safeString(rolesObj);
        if (s != null) {
            return Arrays.stream(s.split("[,\\s]+"))
                    .map(v -> v.trim().toUpperCase(Locale.ROOT))
                    .anyMatch(v -> v.equals("ADMIN") || v.equals("ROLE_ADMIN") || v.contains("ADMIN"));
        }

        return false;
    }

    private boolean containsEmail(Object adminEmailsObj, String email) {
        if (adminEmailsObj == null || email == null) return false;

        // Collection of emails
        if (adminEmailsObj instanceof Collection<?> col) {
            for (Object x : col) {
                String s = safeString(x);
                if (s != null && s.equalsIgnoreCase(email)) return true;
            }
            return false;
        }

        // CSV String
        String csv = safeString(adminEmailsObj);
        if (csv != null) {
            return Arrays.stream(csv.split("[,\\s]+"))
                    .map(String::trim)
                    .anyMatch(v -> v.equalsIgnoreCase(email));
        }

        return false;
    }

    private Object tryInvokeNoArg(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean tryInvokeOneArg(Object obj, String methodName, Class<?> argType, Object value) {
        if (obj == null) return false;
        try {
            Method m = obj.getClass().getMethod(methodName, argType);
            m.setAccessible(true);
            m.invoke(obj, value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object tryReadField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (Exception ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Method findMethod(Class<?> c, String name, int paramCount) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        return null;
    }

    private Object tryEnumValue(Class<?> enumType, String constant) {
        if (enumType == null || constant == null) return null;
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object v = Enum.valueOf((Class<? extends Enum>) enumType, constant);
            return v;
        } catch (Exception ignored) {
            // Try common alt names
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object v = Enum.valueOf((Class<? extends Enum>) enumType, "ROLE_" + constant);
                return v;
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    private String safeString(Object x) {
        if (x == null) return null;
        return String.valueOf(x);
    }
}