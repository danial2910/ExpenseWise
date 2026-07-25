package com.expensewise.support;

import com.expensewise.auth.service.MailService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double that records the last reset link per email instead of
 * sending real mail, so integration tests can drive the full
 * forgot/reset-password flow without SMTP.
 */
@Component
@Primary
public class CapturingMailService implements MailService {

    private final Map<String, String> lastLinkByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        lastLinkByEmail.put(toEmail, resetLink);
    }

    public String lastLinkFor(String email) {
        return lastLinkByEmail.get(email);
    }

    public String extractToken(String email) {
        String link = lastLinkFor(email);
        return link.substring(link.indexOf("token=") + "token=".length());
    }
}
