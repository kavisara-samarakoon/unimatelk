package com.unimatelk.config;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** A server-owned capability bound to one servlet session, independent of its mutable session ID. */
final class ChatHttpSessionBinding implements HttpSessionBindingListener {
    static final String ATTRIBUTE = ChatHttpSessionBinding.class.getName();
    private final HttpSession http;
    private volatile boolean revoked;
    private final Map<WebSocketSession, Runnable> connections = new HashMap<>();

    private ChatHttpSessionBinding(HttpSession http) { this.http = http; }

    static ChatHttpSessionBinding bind(HttpSession http) {
        // Only handshake initialization uses this monitor; no transport I/O occurs here.
        synchronized (http) {
            Object existing = http.getAttribute(ATTRIBUTE);
            if (existing instanceof ChatHttpSessionBinding binding) return binding;
            var binding = new ChatHttpSessionBinding(http);
            http.setAttribute(ATTRIBUTE, binding);
            return binding;
        }
    }

    static ChatHttpSessionBinding from(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get(ATTRIBUTE);
        return value instanceof ChatHttpSessionBinding binding ? binding : null;
    }

    static boolean active(Map<String, Object> attributes, Principal principal) {
        var binding = from(attributes);
        return binding != null && binding.active(principal);
    }

    boolean active(Principal principal) {
        if (revoked || !(principal instanceof Authentication socket) || !socket.isAuthenticated()) return false;
        try {
            Object value = http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (http.getAttribute(ATTRIBUTE) != this || !(value instanceof SecurityContext context)) return false;
            Authentication authentication = context.getAuthentication();
            return !revoked && authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && !(socket instanceof AnonymousAuthenticationToken)
                    && authentication.getName().equals(socket.getName());
        } catch (RuntimeException invalidated) {
            return false;
        }
    }

    synchronized boolean register(WebSocketSession session, Runnable close) {
        if (revoked) return false;
        connections.put(session, close);
        return true;
    }

    synchronized void unregister(WebSocketSession session) { connections.remove(session); }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        ArrayList<Runnable> close;
        synchronized (this) {
            if (revoked) return;
            revoked = true; // Publish denial before cleanup or any potentially failing/blocking close.
            close = new ArrayList<>(connections.values());
            connections.clear();
        }
        // No registry monitor is held during network I/O; one failed close cannot prevent the others.
        for (Runnable action : close) action.run();
    }
}
