package com.expensewise.admin.dto;

import java.time.Instant;

public record RecentSignupResponse(
        Long id,
        String fullName,
        String email,
        Instant createdAt,
        boolean active
) {
}
