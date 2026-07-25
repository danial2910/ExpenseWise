package com.expensewise.health.dto;

import java.time.Instant;

public record HealthResponse(String status, String database, Instant checkedAt) {
}
