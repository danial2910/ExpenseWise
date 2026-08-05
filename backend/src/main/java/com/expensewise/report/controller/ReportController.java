package com.expensewise.report.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.RequiresFeature;
import com.expensewise.report.dto.ExportedReport;
import com.expensewise.report.dto.ReportResponse;
import com.expensewise.report.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * USER-only (SecurityConfig.USER_ONLY_PATHS) and gated under
 * Feature.REPORTS — unlike Dashboard/Profile, Reports is a real,
 * admin-toggleable finance feature. Two endpoints rather than one:
 * {@code GET /reports} returns the JSON data model the screen previews
 * (summary cards, category table, chart) from; {@code GET /reports/download}
 * streams the same data through an exporter as a file. The task's suggested
 * single-URL shape (?format=pdf on the same endpoint) can't serve both a
 * typed JSON body and a binary file from one method signature cleanly, so
 * the download path was split out — see DECISIONS.md.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiresFeature(Feature.REPORTS)
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ReportResponse getReport(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam String type,
                                     @RequestParam Integer year,
                                     @RequestParam(required = false) Integer month) {
        return reportService.buildReport(principal.userId(), type, year, month);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @RequestParam String type,
                                                  @RequestParam Integer year,
                                                  @RequestParam(required = false) Integer month,
                                                  @RequestParam String format) {
        ExportedReport exported = reportService.exportReport(principal.userId(), type, year, month, format);

        // Plain .filename(name) (no explicit Charset) — every generated
        // filename is already ASCII-safe ("expensewise-report-<date>.ext"),
        // and passing an explicit Charset here made Spring RFC 2047-encode
        // the value ("=?UTF-8?Q?...?="), which Chrome's download manager
        // doesn't decode, corrupting the saved filename. See DECISIONS.md.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.filename())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }
}
