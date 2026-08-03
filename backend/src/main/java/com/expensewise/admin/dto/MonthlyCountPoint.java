package com.expensewise.admin.dto;

import java.time.LocalDate;

/** month is always the first day of the month, in Asia/Kuala_Lumpur terms. */
public record MonthlyCountPoint(
        LocalDate month,
        long count
) {
}
