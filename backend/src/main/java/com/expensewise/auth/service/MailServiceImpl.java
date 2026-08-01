package com.expensewise.auth.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the password reset email via Brevo SMTP. If SMTP host is
 * unconfigured (blank, as it is by default in the local profile), the
 * reset link is logged instead of attempting a send that would just fail —
 * this keeps the reset flow usable in local dev without real credentials.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final String smtpHost;
    private final String mailFrom;

    public MailServiceImpl(JavaMailSender javaMailSender,
                            TemplateEngine templateEngine,
                            @Value("${BREVO_SMTP_HOST_EXPENSEWISE:}") String smtpHost,
                            @Value("${MAIL_FROM_EXPENSEWISE:noreply@expensewise.local}") String mailFrom) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.smtpHost = smtpHost;
        this.mailFrom = mailFrom;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        if (smtpHost.isBlank()) {
            log.info("SMTP not configured; password reset link for {}: {}", toEmail, resetLink);
            return;
        }

        Context context = new Context();
        context.setVariable("resetLink", resetLink);
        String html = templateEngine.process("password-reset-email", context);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(mailFrom);
            helper.setSubject("Reset your ExpenseWise password");
            helper.setText(html, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }
}
