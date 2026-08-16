package com.expensewise.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void singleOriginIsAllowedWithCredentials() {
        CorsConfig corsConfig = new CorsConfig("http://localhost:5173");
        CorsRegistry registry = new TestableCorsRegistry();

        corsConfig.corsConfigurer().addCorsMappings(registry);

        CorsConfiguration configuration = corsConfigurations(registry).get("/api/**");
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getExposedHeaders()).contains("Content-Disposition");
    }

    @Test
    void commaSeparatedOriginsAreSplitIntoASeparateAllowedOriginEach() {
        CorsConfig corsConfig = new CorsConfig("https://expensewise.vercel.app,https://staging.expensewise.vercel.app");
        CorsRegistry registry = new TestableCorsRegistry();

        corsConfig.corsConfigurer().addCorsMappings(registry);

        CorsConfiguration configuration = corsConfigurations(registry).get("/api/**");
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://expensewise.vercel.app", "https://staging.expensewise.vercel.app");
    }

    private Map<String, CorsConfiguration> corsConfigurations(CorsRegistry registry) {
        return ((TestableCorsRegistry) registry).exposedCorsConfigurations();
    }

    /** getCorsConfigurations() is protected on CorsRegistry itself; expose it for assertions. */
    private static class TestableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> exposedCorsConfigurations() {
            return getCorsConfigurations();
        }
    }
}
