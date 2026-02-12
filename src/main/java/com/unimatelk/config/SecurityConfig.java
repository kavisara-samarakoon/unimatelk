package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ✅ Enable CSRF using cookie (your frontend already reads XSRF-TOKEN cookie)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Swagger/actuator/ws can be ignored; keep CSRF for /api/** enabled
                        .ignoringRequestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/ws/**"
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // ✅ Public landing + static assets (prevents auto-login loop after logout)
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/images/**",
                                "/uploads/**"
                        ).permitAll()

                        // ✅ Allow login flow endpoints
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()

                        // ✅ Allow these without login so UI can show “Not logged in”
                        .requestMatchers("/api/me", "/api/csrf").permitAll()

                        // ✅ ADMIN ONLY
                        .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")

                        // Everything else requires login
                        .anyRequest().authenticated()
                )

                // ✅ Ensure your DB role -> ROLE_* mapping is used
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                )

                // ✅ Logout must be POST (with CSRF). Also clear cookies & session.
                .logout(logout -> logout
                        .logoutSuccessUrl("/index.html?loggedOut=1")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                );

        return http.build();
    }
}
