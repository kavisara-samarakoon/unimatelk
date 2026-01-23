package com.unimatelk.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public Map<String, Object> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }
}
