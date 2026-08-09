package com.expensewise.report.dto;

import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExportedReport other)) {
            return false;
        }
        return Arrays.equals(content, other.content)
                && Objects.equals(filename, other.filename)
                && Objects.equals(contentType, other.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(content), filename, contentType);
    }

    @Override
    public String toString() {
        return "ExportedReport[content=" + Arrays.toString(content)
                + ", filename=" + filename
                + ", contentType=" + contentType + "]";
    }
}
