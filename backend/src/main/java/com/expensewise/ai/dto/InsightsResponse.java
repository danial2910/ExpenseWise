package com.expensewise.ai.dto;

import java.util.List;

public record InsightsResponse(
        List<InsightResponse> insights
) {
}
