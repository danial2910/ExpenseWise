package com.expensewise.notification.service;

import com.expensewise.notification.entity.Notification;
import com.expensewise.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal helper: writes one row to the notifications table. The full
 * notification system (bill reminders, budget alerts, read/unread UI) is a
 * separate future phase — see CLAUDE.md and DECISIONS.md.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void create(Long userId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}
