package com.expensewise.devsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only backdoor so Playwright can read the raw password-reset token
 * the backend just issued — only its SHA-256 hash is persisted, and the
 * real mail path never runs in local dev (see MailServiceImpl). Registered
 * exclusively under the "local" profile; absent entirely in a prod build.
 */
@RestController
@Profile("local")
public class DevResetTokenController {

    private final DevPasswordResetTokenCache cache;

    public DevResetTokenController(DevPasswordResetTokenCache cache) {
        this.cache = cache;
    }

    @GetMapping("/api/v1/dev/password-reset-token")
    public ResponseEntity<DevResetTokenResponse> lastToken(@RequestParam String email) {
        String token = cache.lastTokenFor(email);
        if (token == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new DevResetTokenResponse(token));
    }
}
