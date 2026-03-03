package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                // Public pages + assets
                .requestMatchers(
                        "/", "/index.html",
                        "/home.html", "/profile.html", "/preferences.html",
                        "/matches.html", "/requests.html", "/chat.html", "/admin.html", "/friends.html",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/uploads/**",
                        "/login**", "/oauth2/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/actuator/health", "/actuator/info", "/actuator/flyway"
                ).permitAll()

                // APIs require login
                .requestMatchers("/api/**").authenticated()

                .anyRequest().permitAll()
        );

        // ✅ IMPORTANT: Return 401 for /api/** instead of redirecting to Google login
        RequestMatcher apiMatcher = request -> {
            String uri = request.getRequestURI();
            return uri != null && uri.startsWith("/api/");
        };

        http.exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        apiMatcher
                )
        );

        // ✅ CSRF cookie kept, but ignore CSRF for APIs + websocket + logout (fixes logout not working)
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                        "/api/**",
                        "/ws/**",
                        "/logout"   // ✅ KEY FIX
                )
        );

        // Force CSRF cookie generation (optional)
        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        // ✅ Always redirect to home after Google login
        http.oauth2Login(oauth -> oauth
                .defaultSuccessUrl("/home.html", true)
        );

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/index.html")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
        );

        return http.build();
    }
}