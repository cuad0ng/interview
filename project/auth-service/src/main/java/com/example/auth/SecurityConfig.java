package com.example.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return username -> accounts.findByUsername(username)
                .map(account -> User.withUsername(account.getUsername())
                        .password(account.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // shortcut: no auth throttling in this local demo, add gateway limits before public deployment.
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/csrf").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/register").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
