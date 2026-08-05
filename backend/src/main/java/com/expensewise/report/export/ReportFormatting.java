package com.expensewise.report.export;

import com.expensewise.report.dto.ReportResponse;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared amount/period formatting for both exporters, so a PDF and an Excel
 * export of the same report always print money the same way — "RM " plus
 * two decimals and thousands grouping, matching the frontend's MoneyDisplay
 * (Intl.NumberFormat('en-MY', ...)).
 */
final class ReportFormatting {

    static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private ReportFormatting() {
    }

    // A fresh DecimalFormat per call — it isn't thread-safe to share, and
    // report exports are low-volume enough that reuse isn't worth the
    // ceremony of a ThreadLocal.
    static String money(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "RM " + format.format(amount);
    }

    static String percent(BigDecimal percentage) {
        return percentage.stripTrailingZeros().toPlainString() + "%";
    }

    static String periodLabel(ReportResponse report) {
        return "MONTHLY".equals(report.type())
                ? MONTH_LABEL.format(report.periodStart())
                : String.valueOf(report.periodStart().getYear());
    }
}
