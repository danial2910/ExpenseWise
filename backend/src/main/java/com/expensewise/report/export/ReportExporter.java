package com.expensewise.report.export;

import com.expensewise.report.dto.ReportResponse;

/**
 * Strategy interface — one implementation per export format. ReportService
 * autowires every bean implementing this and picks one by {@link #format()},
 * so adding a third format later is just adding another {@code @Component}.
 */
public interface ReportExporter {

    byte[] export(ReportResponse report);

    /** Lowercase format key matched against the request's {@code format} query param, e.g. "pdf". */
    String format();

    String contentType();

    /** File extension without a leading dot, e.g. "pdf". */
    String fileExtension();
}
