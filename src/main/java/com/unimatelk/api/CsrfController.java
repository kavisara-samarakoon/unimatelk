package com.unimatelk.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public Map<String, Object> csrf(CsrfToken token) {
        Map<String, Object> out = new HashMap<>();

        // If CSRF is incorrectly disabled for /api/**, token could be null.
        if (token == null) {
            out.put("token", null);
            out.put("headerName", null);
            out.put("error", "CSRF token is null (check SecurityConfig: do NOT ignore /api/**)");
            return out;
        }

        out.put("token", token.getToken());
        out.put("headerName", token.getHeaderName()); // <-- IMPORTANT
        return out;
    }
}
