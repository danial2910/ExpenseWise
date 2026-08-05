package com.expensewise.report.export;

import com.expensewise.transaction.dto.TransactionResponse;

/**
 * A plain JavaBean view of one transaction row for JasperReports' field
 * binding. JasperReports' JRBeanCollectionDataSource uses standard
 * getXxx()/isXxx() introspection, which a Java record's accessor methods
 * (e.g. {@code transactionDate()}, no "get" prefix) don't satisfy — this
 * class exists purely to bridge that gap, with money/date already formatted
 * as display strings so the .jrxml template stays free of formatting logic.
 * Must be public: java.beans.Introspector (which JasperReports' bean data
 * source uses under the hood) only discovers accessor methods declared on a
 * public class, even if the methods themselves are public.
 */
public class JasperTransactionRow {

    private final String date;
    private final String type;
    private final String categoryName;
    private final String description;
    private final String amountDisplay;

    public JasperTransactionRow(TransactionResponse transaction) {
        this.date = transaction.transactionDate().toString();
        this.type = transaction.type();
        this.categoryName = transaction.categoryName();
        this.description = transaction.description() != null ? transaction.description() : "";
        this.amountDisplay = ReportFormatting.money(transaction.amount());
    }

    public String getDate() {
        return date;
    }

    public String getType() {
        return type;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public String getAmountDisplay() {
        return amountDisplay;
    }
}
