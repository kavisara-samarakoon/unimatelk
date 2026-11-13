package com.unimatelk.config;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

final class ChatSessionTestSupport {
    static MockHttpSession http(Principal principal) {
        var http = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        if (principal instanceof Authentication auth) context.setAuthentication(auth);
        http.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return http;
    }

    static Map<String, Object> attributes(Principal principal) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatHttpSessionBinding.ATTRIBUTE, ChatHttpSessionBinding.bind(http(principal)));
        return attributes;
    }
}
