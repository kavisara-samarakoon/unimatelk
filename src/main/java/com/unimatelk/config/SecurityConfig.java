package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                // Public pages + assets
                .requestMatchers(
                        "/", "/index.html",
                        "/home.html", "/profile.html", "/preferences.html",
                        "/matches.html", "/requests.html", "/chat.html", "/admin.html",
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

        // ✅ Keep CSRF cookie for future, BUT ignore CSRF for your app APIs to avoid 403
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                        "/api/**",     // <--- IMPORTANT: avoids 403 on POST/PUT during demo
                        "/ws/**"
                )
        );

        // Force CSRF cookie generation (optional)
        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        http.oauth2Login(Customizer.withDefaults());

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/index.html")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
        );

        return http.build();
    }
}