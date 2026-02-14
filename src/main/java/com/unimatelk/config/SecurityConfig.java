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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/uploads/**", "/favicon.ico",
                                "/login**", "/oauth2/**"
                        ).permitAll()

                        // ✅ Let admin page load after login
                        .requestMatchers("/admin.html").authenticated()

                        // ✅ Allow calling admin APIs only if logged in (ADMIN check is done in controller using DB)
                        .requestMatchers("/api/admin/**").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth.defaultSuccessUrl("/index.html", true))
                .logout(logout -> logout.logoutSuccessUrl("/index.html"));

        return http.build();
    }
}
