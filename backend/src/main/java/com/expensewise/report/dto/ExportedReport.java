package com.expensewise.report.dto;

/**
 * The bytes a ReportExporter produced, plus what the controller needs to set
 * the download headers. Never serialized to JSON or handed to a second
 * caller who could mutate the array back into shared state — it's built and
 * consumed once, inside ReportService.exportReport/ReportController — so the
 * defensive-copy rules for exposing a mutable array are suppressed here
 * rather than paying to copy a multi-KB report file for no real benefit.
 */
@SuppressWarnings({"java:S2384", "java:S2386"})
public record ExportedReport(
        byte[] content,
        String filename,
        String contentType
) {
}
