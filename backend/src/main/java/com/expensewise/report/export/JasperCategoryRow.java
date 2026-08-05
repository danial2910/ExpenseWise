package com.expensewise.report.export;

import com.expensewise.report.dto.CategoryBreakdownLine;

/** JavaBean view of one category-breakdown row — see {@link JasperTransactionRow} for why this exists (and why public). */
public class JasperCategoryRow {

    private final String name;
    private final String amountDisplay;
    private final String percentageDisplay;

    public JasperCategoryRow(CategoryBreakdownLine line) {
        this.name = line.categoryName();
        this.amountDisplay = ReportFormatting.money(line.amount());
        this.percentageDisplay = ReportFormatting.percent(line.percentage());
    }

    public String getName() {
        return name;
    }

    public String getAmountDisplay() {
        return amountDisplay;
    }

    public String getPercentageDisplay() {
        return percentageDisplay;
    }
}
