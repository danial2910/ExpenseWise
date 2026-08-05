package com.expensewise.config;

import com.expensewise.auth.security.JwtAuthenticationFilter;
import com.expensewise.auth.security.RestAccessDeniedHandler;
import com.expensewise.auth.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    // ADMIN accounts may only reach Admin Dashboard/User Management, AI
    // Assistant, and Profile — never the personal-finance modules. Blocking
    // it here (path-level, defense in depth alongside @RequiresFeature) means
    // an ADMIN gets a 403 even before a controller method runs.
    private static final String[] USER_ONLY_PATHS = {
            "/api/v1/transactions/**",
            "/api/v1/budgets/**",
            "/api/v1/categories/**",
            "/api/v1/reports/**",
            "/api/v1/news/**",
            "/api/v1/dashboard/**"
    };

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            // Phase 1 built its own health endpoint rather than adding the
            // actuator starter — this is the real one, not /actuator/health.
            "/api/v1/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // Only ever mapped under the "local" profile (DevResetTokenController) —
            // permitting the path here is harmless elsewhere since no controller
            // is registered for it outside local, so it 404s regardless.
            "/api/v1/dev/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     RestAuthenticationEntryPoint authenticationEntryPoint,
                                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(USER_ONLY_PATHS).hasRole("USER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
