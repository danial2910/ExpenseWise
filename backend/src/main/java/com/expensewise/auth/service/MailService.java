package com.expensewise.auth.service;

public interface MailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);

    void sendSetPasswordEmail(String toEmail, String setPasswordLink);
}
