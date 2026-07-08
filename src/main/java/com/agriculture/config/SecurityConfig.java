package com.agriculture.config;

import com.agriculture.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/face/login").permitAll()
                        .requestMatchers("/api/auth/face/status").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/ws/**").permitAll()
                        .requestMatchers("/api/mqtt/**").permitAll()
                        .requestMatchers("/api/iotda/**").permitAll()
                        .requestMatchers("/api/pest/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plots/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/telemetry/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/irrigation/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/alarms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/strategies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/operation-logs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/water-limits/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/light/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/light-strategies/**").permitAll()
                        .requestMatchers("/api/plots/**").authenticated()
                        .requestMatchers("/api/devices/**").authenticated()
                        .requestMatchers("/api/control/**").authenticated()
                        .requestMatchers("/api/light/**").authenticated()
                        .requestMatchers("/api/light-strategies/**").authenticated()
                        .requestMatchers("/api/alarms/**").authenticated()
                        .requestMatchers("/api/strategies/**").authenticated()
                        .requestMatchers("/api/water-limits/**").authenticated()
                        .requestMatchers("/api/operation-logs/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
