package com.expensewise.entitlement;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.service.EntitlementService;
import com.expensewise.exception.FeatureNotEnabledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureEntitlementInterceptorTest {

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private FeatureEntitlementInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new FeatureEntitlementInterceptor(entitlementService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, role), null));
    }

    private HandlerMethod gatedHandlerMethod() throws NoSuchMethodException {
        Method method = GatedController.class.getMethod("handle");
        return new HandlerMethod(new GatedController(), method);
    }

    @Test
    void nonHandlerMethodTargetsAlwaysPass() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void adminAlwaysBypassesTheEntitlementCheck() throws NoSuchMethodException {
        authenticateAs(1L, "ADMIN");

        assertThat(interceptor.preHandle(request, response, gatedHandlerMethod())).isTrue();
    }

    @Test
    void userWithTheFeatureEnabledPasses() throws NoSuchMethodException {
        authenticateAs(2L, "USER");
        when(entitlementService.isEnabled(2L, Feature.BUDGETS)).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, gatedHandlerMethod())).isTrue();
    }

    @Test
    void userWithTheFeatureDisabledIsRejected() throws NoSuchMethodException {
        authenticateAs(3L, "USER");
        when(entitlementService.isEnabled(3L, Feature.BUDGETS)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, gatedHandlerMethod()))
                .isInstanceOf(FeatureNotEnabledException.class);
    }

    @RequiresFeature(Feature.BUDGETS)
    static class GatedController {
        public void handle() {
        }
    }
}
