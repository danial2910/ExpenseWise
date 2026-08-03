package com.expensewise.entitlement;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.service.EntitlementService;
import com.expensewise.exception.FeatureNotEnabledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Cross-cutting enforcement for {@link RequiresFeature}. Checked on every
 * request whose handler method (or its declaring controller) carries the
 * annotation — the single place this rule is evaluated, so no controller
 * needs its own if-checks. ADMIN principals always pass: entitlements only
 * ever govern USER accounts (ADMIN's allowed module set is controlled by
 * {@code SecurityConfig} role matchers instead).
 */
@Component
public class FeatureEntitlementInterceptor implements HandlerInterceptor {

    private final EntitlementService entitlementService;

    public FeatureEntitlementInterceptor(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresFeature annotation = handlerMethod.getMethodAnnotation(RequiresFeature.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresFeature.class);
        }
        if (annotation == null) {
            return true;
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AuthPrincipal authPrincipal)) {
            return true;
        }
        if ("ADMIN".equals(authPrincipal.role())) {
            return true;
        }

        if (!entitlementService.isEnabled(authPrincipal.userId(), annotation.value())) {
            throw new FeatureNotEnabledException("This feature is not enabled for your account");
        }
        return true;
    }
}
