package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Swagger "Try it out" does not send CSRF token, so PUT/POST gets 403.
                // Easiest dev fix: ignore CSRF for API + Swagger endpoints.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/ws/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**"
                ))


                .authorizeHttpRequests(auth -> auth
                        // Allow Swagger + actuator health/info without login
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Everything else requires login
                        .anyRequest().authenticated()
                )

                // If you are using Google OAuth2 login (you are), keep this
                .oauth2Login(Customizer.withDefaults())

                .logout(logout -> logout.logoutSuccessUrl("/").permitAll());

        return http.build();
    }
}
