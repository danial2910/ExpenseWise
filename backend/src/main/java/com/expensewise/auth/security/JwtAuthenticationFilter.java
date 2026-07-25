package com.expensewise.auth.security;

import com.expensewise.auth.service.JwtService;
import com.expensewise.common.UserStatusCache;
import com.expensewise.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Validates the access token on every request and, if valid, populates the
 * security context. Also enforces that a disabled user is rejected even
 * with a currently-valid token, by checking {@link UserStatusCache} here
 * rather than only at login time.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserStatusCache userStatusCache;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                    UserStatusCache userStatusCache) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userStatusCache = userStatusCache;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractBearerToken(request)
                .flatMap(jwtService::parse)
                .ifPresent(claims -> authenticateIfActive(claims));

        filterChain.doFilter(request, response);
    }

    private void authenticateIfActive(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);

        boolean active = userStatusCache.isActive(userId,
                () -> userRepository.findActiveById(userId).orElse(false));
        if (!active) {
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(userId, role);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        return Optional.empty();
    }
}
