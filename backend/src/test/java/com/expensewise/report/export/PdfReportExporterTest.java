package com.expensewise.report.export;

import com.expensewise.report.dto.BudgetSummaryResponse;
import com.expensewise.report.dto.CategoryBreakdownLine;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates a real PDF via the compiled report.jrxml/category-breakdown-subreport.jrxml
 * templates — no mocking, since both JasperReports and the templates are
 * local, offline dependencies (CLAUDE.md's Reports stack note).
 */
class PdfReportExporterTest {

    private final PdfReportExporter exporter = new PdfReportExporter();

    private TransactionResponse transaction(String type, String amount, Long categoryId, String categoryName,
                                             LocalDate date, String description) {
        return new TransactionResponse(1L, type, new BigDecimal(amount), categoryId, categoryName, "utensils",
                date, description, Instant.now(), Instant.now(), null);
    }

    private ReportResponse monthlyReport() {
        List<TransactionResponse> transactions = List.of(
                transaction("INCOME", "3000.00", 20L, "Salary", LocalDate.of(2026, Month.MARCH, 1), "Payday"),
                transaction("EXPENSE", "80.00", 10L, "Food", LocalDate.of(2026, Month.MARCH, 5), "Groceries"));
        List<CategoryBreakdownLine> categories = List.of(
                new CategoryBreakdownLine(10L, "Food", "utensils", new BigDecimal("80.00"), new BigDecimal("100")));
        BudgetSummaryResponse budget = new BudgetSummaryResponse(
                new BigDecimal("500.00"), new BigDecimal("80.00"), new BigDecimal("420.00"), true);
        return new ReportResponse("MONTHLY", LocalDate.of(2026, Month.MARCH, 1), LocalDate.of(2026, Month.MARCH, 31),
                new BigDecimal("3000.00"), new BigDecimal("80.00"), new BigDecimal("2920.00"),
                categories, transactions, budget, List.of());
    }

    @Test
    void generatesNonEmptyPdfBytesWithTheCorrectMagicHeader() {
        byte[] pdf = exporter.export(monthlyReport());

        assertThat(pdf).isNotEmpty();
        // "%PDF" — every valid PDF file starts with this magic header.
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void reportsTheCorrectContentTypeAndFileExtension() {
        assertThat(exporter.format()).isEqualTo("pdf");
        assertThat(exporter.contentType()).isEqualTo("application/pdf");
        assertThat(exporter.fileExtension()).isEqualTo("pdf");
    }

    @Test
    void generatesAPdfForAPeriodWithNoTransactionsOrBudget() {
        ReportResponse empty = new ReportResponse("YEARLY", LocalDate.of(2025, Month.JANUARY, 1),
                LocalDate.of(2025, Month.DECEMBER, 31), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), new BudgetSummaryResponse(null, BigDecimal.ZERO, null, false), List.of());

        byte[] pdf = exporter.export(empty);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
