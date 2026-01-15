package com.unimatelk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Public pages + assets
                        .requestMatchers(
                                "/", "/index.html",
<<<<<<< Updated upstream
                                "/css/**", "/js/**", "/images/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/me",
                                "/oauth2/**",
                                "/login**"
=======
                                "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",

                                // ✅ IMPORTANT: allow /api/me so it can return authenticated:false instead of redirecting
                                "/api/me"
>>>>>>> Stashed changes
                        ).permitAll()

                        // Everything else needs login
                        .anyRequest().authenticated()
                )
<<<<<<< Updated upstream
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/index.html"));
=======

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        // ✅ ALWAYS go back to UI after login (prevents /api/me?continue landing)
                        .defaultSuccessUrl("/index.html", true)
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/index.html")
                )

                // keep defaults
                .httpBasic(Customizer.withDefaults());
>>>>>>> Stashed changes

        return http.build();
    }
}
