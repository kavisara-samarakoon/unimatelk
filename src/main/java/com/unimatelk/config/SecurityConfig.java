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
                // ✅ IMPORTANT: Use Cookie CSRF so your frontend can read XSRF-TOKEN cookie
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Swagger + SockJS can break with CSRF, so ignore these only
                        .ignoringRequestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/ws/**"
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // ✅ Public pages/assets (so after logout it won’t auto-login again)
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/images/**",
                                "/uploads/**",
                                "/error"
                        ).permitAll()

                        // ✅ OAuth endpoints must be public
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()

                        // ✅ Needed so UI can show “Not logged in” and so CSRF cookie can be created
                        .requestMatchers("/api/me", "/api/csrf").permitAll()

                        // ✅ Admin protection
                        .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")

                        // Everything else requires login
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                )

                // ✅ Logout must invalidate session + delete cookies
                .logout(logout -> logout
                        .logoutUrl("/logout") // default, but explicit is clearer
                        .logoutSuccessUrl("/index.html?loggedOut=1")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                );

        return http.build();
    }
}
