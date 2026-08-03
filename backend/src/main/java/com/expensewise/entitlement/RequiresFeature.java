package com.expensewise.entitlement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (class-level) or a single endpoint (method-level) as
 * gated behind a {@link Feature} entitlement. Enforced by
 * {@code FeatureEntitlementInterceptor} — never trust the client, the check
 * always runs server-side against the authenticated principal's own
 * entitlements. ADMIN principals always bypass this check; entitlements only
 * ever apply to USER accounts.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresFeature {

    Feature value();
}
