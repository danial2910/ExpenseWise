package com.expensewise.auth.service;

import com.expensewise.devsupport.DevPasswordResetTokenCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends the password reset email through Brevo's transactional email HTTP
 * API (not SMTP — some hosting platforms block outbound SMTP ports on
 * free/low tiers, while outbound HTTPS always works). If the API key is
 * unconfigured (blank, as it is by default in the local profile), the
 * reset link is logged instead of attempting a call that would just fail —
 * this keeps the reset flow usable in local dev without real credentials.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
    private static final String BREVO_SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final TemplateEngine templateEngine;
    private final String apiKey;
    private final String mailFrom;
    private final Optional<DevPasswordResetTokenCache> devTokenCache;

    public MailServiceImpl(TemplateEngine templateEngine,
                            @Value("${BREVO_API_KEY_EXPENSEWISE:}") String apiKey,
                            @Value("${MAIL_FROM_EXPENSEWISE:noreply@expensewise.local}") String mailFrom,
                            Optional<DevPasswordResetTokenCache> devTokenCache) {
        this.restClient = RestClient.create();
        this.templateEngine = templateEngine;
        this.apiKey = apiKey;
        this.mailFrom = mailFrom;
        this.devTokenCache = devTokenCache;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        devTokenCache.ifPresent(cache -> cache.capture(toEmail, resetLink));

        if (apiKey.isBlank()) {
            log.info("Brevo API key not configured; password reset link for {}: {}", toEmail, resetLink);
            return;
        }

        Context context = new Context();
        context.setVariable("resetLink", resetLink);
        String html = templateEngine.process("password-reset-email", context);

        Map<String, Object> requestBody = Map.of(
                "sender", Map.of("email", mailFrom),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "Reset your ExpenseWise password",
                "htmlContent", html
        );

        try {
            restClient.post()
                    .uri(BREVO_SEND_EMAIL_URL)
                    .header("api-key", apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }
}
