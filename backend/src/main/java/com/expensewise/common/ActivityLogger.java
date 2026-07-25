package com.expensewise.common;

import com.expensewise.common.entity.ActivityLog;
import com.expensewise.common.repository.ActivityLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit trail entries. Never pass a password, raw token, or JWT into
 * {@code action}/{@code entityType} — only event names and ids.
 *
 * <p>Runs in its own new transaction: several callers (e.g. a failed login)
 * log an event and then deliberately throw, which would otherwise roll
 * back this insert along with the rest of that transaction. Audit entries
 * must survive regardless of how the calling operation ends.
 */
@Component
public class ActivityLogger {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogger(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action) {
        save(userId, action, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String ipAddress) {
        save(userId, action, null, null, ipAddress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, Long entityId, String ipAddress) {
        save(userId, action, entityType, entityId, ipAddress);
    }

    private void save(Long userId, String action, String entityType, Long entityId, String ipAddress) {
        ActivityLog entry = new ActivityLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(ipAddress);
        activityLogRepository.save(entry);
    }
}
