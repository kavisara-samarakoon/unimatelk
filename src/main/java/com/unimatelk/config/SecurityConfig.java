package com.unimatelk.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                // Public pages + assets
                .requestMatchers(
                        "/", "/index.html",
                        "/home.html", "/profile.html", "/preferences.html",
                        "/matches.html", "/requests.html", "/chat.html", "/admin.html", "/friends.html",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/login**", "/oauth2/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/actuator/health", "/actuator/info", "/actuator/flyway"
                ).permitAll()

                // APIs require login
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                // Disable insecure password recovery until repaired.
                .requestMatchers(HttpMethod.POST, "/api/auth/forgot", "/api/auth/reset").denyAll()
                .requestMatchers("/api/**").authenticated()

                // Only controller-backed media routes may read uploaded files.
                .requestMatchers(HttpMethod.GET, "/uploads/chat/{roomId}/{filename}",
                        "/uploads/profiles/{userId}/{filename}", "/uploads/{filename}").authenticated()
                .requestMatchers(HttpMethod.HEAD, "/uploads/chat/{roomId}/{filename}",
                        "/uploads/profiles/{userId}/{filename}", "/uploads/{filename}").authenticated()
                .requestMatchers("/uploads", "/uploads/**").denyAll()

                .anyRequest().permitAll()
        );

        // APIs and protected media return 401 instead of a login redirect.
        RequestMatcher apiMatcher = request -> {
            String uri = request.getRequestURI();
            String path = uri == null ? "" : uri.substring(request.getContextPath().length());
            return path.startsWith("/api/") || path.equals("/uploads") || path.startsWith("/uploads/");
        };

        http.exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        apiMatcher
                )
        );

        // Enforce CSRF for API writes and logout; WebSocket paths remain temporarily exempt.
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/ws/**")
        );

        // Force CSRF cookie generation (optional)
        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        // ✅ Always redirect to home after Google login
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .defaultSuccessUrl("/home.html", true)
            );
        }

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/index.html")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
        );

        return http.build();
    }
}
