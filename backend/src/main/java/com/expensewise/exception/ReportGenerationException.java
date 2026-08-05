package com.expensewise.exception;

/**
 * Wraps a checked failure from POI/JasperReports (both throw checked
 * exceptions) into something that can propagate through ReportExporter's
 * interface method. Always unexpected — the report data is already valid by
 * the time an exporter runs — so it has no dedicated handler and falls
 * through to GlobalExceptionHandler's generic 500.
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
