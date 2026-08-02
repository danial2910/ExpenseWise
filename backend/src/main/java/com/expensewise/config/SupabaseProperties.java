package com.expensewise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bound with explicit @Value placeholders naming the exact env var, not
 * @ConfigurationProperties relaxed binding — see GroqProperties for why
 * that matters (a plain, unrelated env var of the derived name can
 * silently win over the real *_EXPENSEWISE one). serviceKey is the
 * Supabase service_role key: backend-only, must never reach the frontend.
 */
@Component
public record SupabaseProperties(
        @Value("${SUPABASE_URL_EXPENSEWISE}") String url,
        @Value("${SUPABASE_SERVICE_KEY_EXPENSEWISE}") String serviceKey,
        @Value("${SUPABASE_BUCKET_EXPENSEWISE}") String bucket
) {
}
