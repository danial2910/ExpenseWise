package com.expensewise.devsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the last password-reset link issued per email, purely so the
 * Playwright e2e suite can read the raw token without a real mailbox — only
 * its SHA-256 hash is ever persisted (see PasswordResetService). Wired only
 * under the "local" profile; does not exist in a prod deployment.
 */
@Component
@Profile("local")
public class DevPasswordResetTokenCache {

    private static final String TOKEN_PARAM = "token=";

    private final Map<String, String> lastLinkByEmail = new ConcurrentHashMap<>();

    public void capture(String email, String resetLink) {
        lastLinkByEmail.put(email, resetLink);
    }

    public String lastTokenFor(String email) {
        String link = lastLinkByEmail.get(email);
        if (link == null) {
            return null;
        }
        return link.substring(link.indexOf(TOKEN_PARAM) + TOKEN_PARAM.length());
    }
}
