package com.expensewise.auth.service;

public interface MailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
