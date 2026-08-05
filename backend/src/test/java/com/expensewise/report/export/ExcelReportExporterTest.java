package com.expensewise.report.export;

import com.expensewise.report.dto.BudgetSummaryResponse;
import com.expensewise.report.dto.CategoryBreakdownLine;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.transaction.dto.TransactionResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates a real .xlsx workbook and reads it back with POI to assert on
 * actual cell contents — no mocking, per CLAUDE.md (POI is a local, offline
 * library, so this is cheap and exercises the real serialization).
 */
class ExcelReportExporterTest {

    private final ExcelReportExporter exporter = new ExcelReportExporter();

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
    void generatesAWorkbookWithSummaryAndTransactionsSheetsMatchingTheReportData() throws IOException {
        byte[] bytes = exporter.export(monthlyReport());
        assertThat(bytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);

            Sheet summary = workbook.getSheet("Summary");
            assertThat(summary).isNotNull();
            assertThat(findRowContaining(summary, "Total Income").getCell(1).getNumericCellValue())
                    .isEqualTo(3000.00);
            assertThat(findRowContaining(summary, "Total Expense").getCell(1).getNumericCellValue())
                    .isEqualTo(80.00);
            assertThat(findRowContaining(summary, "Net Balance").getCell(1).getNumericCellValue())
                    .isEqualTo(2920.00);
            assertThat(findRowContaining(summary, "Budgeted").getCell(1).getNumericCellValue())
                    .isEqualTo(500.00);
            assertThat(findRowContaining(summary, "Food").getCell(1).getNumericCellValue())
                    .isEqualTo(80.00);

            Sheet transactions = workbook.getSheet("Transactions");
            assertThat(transactions).isNotNull();
            Row header = transactions.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Amount");

            Row firstDataRow = transactions.getRow(1);
            assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("INCOME");
            assertThat(firstDataRow.getCell(3).getStringCellValue()).isEqualTo("Payday");
            assertThat(firstDataRow.getCell(4).getNumericCellValue()).isEqualTo(3000.00);
        }
    }

    @Test
    void reportsTheCorrectContentTypeAndFileExtension() {
        assertThat(exporter.format()).isEqualTo("excel");
        assertThat(exporter.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(exporter.fileExtension()).isEqualTo("xlsx");
    }

    @Test
    void generatesAWorkbookForAPeriodWithNoTransactionsOrBudget() throws IOException {
        ReportResponse empty = new ReportResponse("YEARLY", LocalDate.of(2025, Month.JANUARY, 1),
                LocalDate.of(2025, Month.DECEMBER, 31), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), new BudgetSummaryResponse(null, BigDecimal.ZERO, null, false), List.of());

        byte[] bytes = exporter.export(empty);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Transactions").getPhysicalNumberOfRows()).isEqualTo(1); // header only
        }
    }

    private Row findRowContaining(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("No row found with label: " + label);
    }
}
