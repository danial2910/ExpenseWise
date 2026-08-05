package com.expensewise.report.export;

import com.expensewise.exception.ReportGenerationException;
import com.expensewise.report.dto.ReportResponse;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles report.jrxml and its category-breakdown subreport once at
 * startup (compilation is the expensive step) and fills them per request.
 * The main datasource is the period's transactions; category breakdown is a
 * nested subreport fed its own bean collection, so the top-level template
 * stays a single flat transaction table plus a small summary header — see
 * the .jrxml files under src/main/resources/reports for the layout itself.
 */
@Component
public class PdfReportExporter implements ReportExporter {

    private final JasperReport mainReport;
    private final JasperReport categorySubreport;

    public PdfReportExporter() {
        this.categorySubreport = compile("reports/category-breakdown-subreport.jrxml");
        this.mainReport = compile("reports/report.jrxml");
    }

    @Override
    public byte[] export(ReportResponse report) {
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("REPORT_TITLE", "MONTHLY".equals(report.type()) ? "Monthly Report" : "Yearly Report");
            parameters.put("PERIOD_LABEL", ReportFormatting.periodLabel(report));
            parameters.put("TOTAL_INCOME_DISPLAY", ReportFormatting.money(report.totalIncome()));
            parameters.put("TOTAL_EXPENSE_DISPLAY", ReportFormatting.money(report.totalExpense()));
            parameters.put("NET_BALANCE_DISPLAY", ReportFormatting.money(report.netBalance()));
            parameters.put("BUDGETED_DISPLAY", ReportFormatting.money(report.budgetSummary().totalBudgeted()));
            parameters.put("SPENT_DISPLAY", ReportFormatting.money(report.budgetSummary().totalSpent()));
            parameters.put("REMAINING_DISPLAY", ReportFormatting.money(report.budgetSummary().totalRemaining()));
            parameters.put("CATEGORY_SUBREPORT", categorySubreport);
            parameters.put("CATEGORY_DATASOURCE", new JRBeanCollectionDataSource(
                    report.categoryBreakdown().stream().map(JasperCategoryRow::new).toList()));

            List<JasperTransactionRow> rows = report.transactions().stream().map(JasperTransactionRow::new).toList();
            JasperPrint jasperPrint = JasperFillManager.fillReport(mainReport, parameters,
                    new JRBeanCollectionDataSource(rows));

            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (JRException e) {
            throw new ReportGenerationException("Failed to generate PDF report", e);
        }
    }

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String fileExtension() {
        return "pdf";
    }

    private JasperReport compile(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return JasperCompileManager.compileReport(in);
        } catch (JRException | IOException e) {
            throw new ReportGenerationException("Failed to compile report template: " + classpathLocation, e);
        }
    }
}
