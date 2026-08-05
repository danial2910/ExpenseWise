package com.expensewise.report.export;

import com.expensewise.exception.ReportGenerationException;
import com.expensewise.report.dto.CategoryBreakdownLine;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.transaction.dto.TransactionResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * A "Summary" sheet (totals, budget, category breakdown) and a
 * "Transactions" sheet (every transaction in the period) — real numeric
 * cells with a currency data format, not pre-formatted text, so the export
 * stays usable for further calculation in a spreadsheet, unlike the PDF.
 */
@Component
public class ExcelReportExporter implements ReportExporter {

    private static final String[] SUMMARY_LABELS = {"Total Income", "Total Expense", "Net Balance"};
    private static final String[] CATEGORY_HEADERS = {"Category", "Amount", "Share"};
    private static final String[] TRANSACTION_HEADERS = {"Date", "Type", "Category", "Description", "Amount"};

    @Override
    public byte[] export(ReportResponse report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle boldStyle = boldStyle(workbook);
            CellStyle moneyStyle = moneyStyle(workbook);
            CellStyle percentStyle = percentStyle(workbook);

            buildSummarySheet(workbook, report, boldStyle, moneyStyle, percentStyle);
            buildTransactionsSheet(workbook, report, boldStyle, moneyStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
    }

    @Override
    public String format() {
        return "excel";
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String fileExtension() {
        return "xlsx";
    }

    private void buildSummarySheet(XSSFWorkbook workbook, ReportResponse report, CellStyle boldStyle,
                                    CellStyle moneyStyle, CellStyle percentStyle) {
        Sheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;

        rowNum = titleRow(sheet, rowNum, "ExpenseWise Report — " + ReportFormatting.periodLabel(report), boldStyle);
        rowNum++;

        BigDecimal[] totals = {report.totalIncome(), report.totalExpense(), report.netBalance()};
        for (int i = 0; i < SUMMARY_LABELS.length; i++) {
            rowNum = labelledAmountRow(sheet, rowNum, SUMMARY_LABELS[i], totals[i], moneyStyle);
        }
        rowNum++;

        rowNum = titleRow(sheet, rowNum, "Budget", boldStyle);
        var budget = report.budgetSummary();
        rowNum = labelledAmountRow(sheet, rowNum, "Budgeted", budget.totalBudgeted(), moneyStyle);
        rowNum = labelledAmountRow(sheet, rowNum, "Spent", budget.totalSpent(), moneyStyle);
        rowNum = labelledAmountRow(sheet, rowNum, "Remaining", budget.totalRemaining(), moneyStyle);
        rowNum++;

        rowNum = titleRow(sheet, rowNum, "Category Breakdown", boldStyle);
        rowNum = headerRow(sheet, rowNum, boldStyle, CATEGORY_HEADERS);
        for (CategoryBreakdownLine line : report.categoryBreakdown()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(line.categoryName());
            moneyCell(row, 1, line.amount(), moneyStyle);
            percentCell(row, 2, line.percentage(), percentStyle);
        }

        for (int col = 0; col < CATEGORY_HEADERS.length; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private void buildTransactionsSheet(XSSFWorkbook workbook, ReportResponse report, CellStyle boldStyle,
                                         CellStyle moneyStyle) {
        Sheet sheet = workbook.createSheet("Transactions");
        int rowNum = headerRow(sheet, 0, boldStyle, TRANSACTION_HEADERS);

        for (TransactionResponse transaction : report.transactions()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(transaction.transactionDate().toString());
            row.createCell(1).setCellValue(transaction.type());
            row.createCell(2).setCellValue(transaction.categoryName());
            row.createCell(3).setCellValue(transaction.description() != null ? transaction.description() : "");
            moneyCell(row, 4, transaction.amount(), moneyStyle);
        }

        for (int col = 0; col < TRANSACTION_HEADERS.length; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private int titleRow(Sheet sheet, int rowNum, String text, CellStyle boldStyle) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(boldStyle);
        return rowNum + 1;
    }

    private int headerRow(Sheet sheet, int rowNum, CellStyle boldStyle, String... headers) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(boldStyle);
        }
        return rowNum + 1;
    }

    private int labelledAmountRow(Sheet sheet, int rowNum, String label, BigDecimal amount, CellStyle moneyStyle) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        moneyCell(row, 1, amount, moneyStyle);
        return rowNum + 1;
    }

    private void moneyCell(Row row, int col, BigDecimal amount, CellStyle moneyStyle) {
        Cell cell = row.createCell(col);
        if (amount == null) {
            cell.setCellValue("—");
            return;
        }
        cell.setCellValue(amount.doubleValue());
        cell.setCellStyle(moneyStyle);
    }

    private void percentCell(Row row, int col, BigDecimal percentage, CellStyle percentStyle) {
        Cell cell = row.createCell(col);
        cell.setCellValue(percentage.doubleValue());
        cell.setCellStyle(percentStyle);
    }

    private CellStyle boldStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle moneyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("\"RM \"#,##0.00"));
        return style;
    }

    private CellStyle percentStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0\"%\""));
        return style;
    }
}
