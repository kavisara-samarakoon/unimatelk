package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ✅ CSRF token cookie for JS (XSRF-TOKEN)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                )

                .authorizeHttpRequests(auth -> auth
                        // ✅ public assets
                        .requestMatchers(
                                "/", "/index.html", "/error",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico"
                        ).permitAll()

                        // ✅ admin page needs login
                        .requestMatchers("/admin.html").authenticated()

                        // ✅ APIs require login (admin API still checks ADMIN role)
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().permitAll()
                )

                .oauth2Login(Customizer.withDefaults())

                .logout(logout -> logout
                        .logoutSuccessUrl("/index.html")
                        .permitAll()
                )

                // ✅ IMPORTANT: for /api/** return 401 JSON instead of redirect HTML
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                (RequestMatcher) request -> request.getRequestURI().startsWith("/api/")
                        )
                );

        return http.build();
    }
}
