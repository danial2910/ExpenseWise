package com.expensewise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * allowedOrigins is bound from app.cors.allowed-origins (backed by
 * APP_CORS_ALLOWED_ORIGINS_EXPENSEWISE, see application.yml), defaulting to
 * http://localhost:5173 so local/CI/test are unchanged. Prod sets it to the
 * deployed Vercel URL. Always an explicit origin list, never "*" — required
 * because allowCredentials(true) is set below (the refresh-token cookie).
 */
@Configuration
public class CorsConfig {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        // Content-Disposition isn't on the browser's default
                        // cross-origin response-header safelist — without this,
                        // the Reports module's frontend can't read the
                        // server-suggested filename off a download response.
                        .exposedHeaders("Content-Disposition")
                        .allowCredentials(true);
            }
        };
    }
}
