package com.unimatelk.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** Copy an opaque binding from the authenticated servlet request, never a client-supplied session ID. */
public class ChatSessionHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            if (request instanceof ServletServerHttpRequest servlet) {
                var http = servlet.getServletRequest().getSession(false);
                if (http != null) {
                    var binding = ChatHttpSessionBinding.bind(http);
                    if (binding.active(request.getPrincipal())) {
                        attributes.put(ChatHttpSessionBinding.ATTRIBUTE, binding);
                        return true;
                    }
                }
            }
        } catch (RuntimeException invalidated) {
            // Session destruction racing the handshake must fail closed without exposing its ID.
        }
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }
}
