package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
                // ✅ IMPORTANT: Use COOKIE-based CSRF (works with your api.js: XSRF-TOKEN + X-XSRF-TOKEN)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Ignore only these (Swagger + websocket handshake)
                        .ignoringRequestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/ws/**"
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // ✅ Public pages + static assets (so home page shows login/logout correctly)
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/images/**", "/uploads/**",
                                "/error", "/favicon.ico"
                        ).permitAll()

                        // ✅ OAuth endpoints public
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()

                        // ✅ These MUST be public
                        .requestMatchers("/api/me", "/api/csrf").permitAll()

                        // ✅ Admin protection
                        .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")

                        // Everything else requires login
                        .anyRequest().authenticated()
                )

                // ✅ Use your custom user service so roles (ADMIN) are assigned correctly
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                )

                // ✅ Logout should invalidate session + remove cookies
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
