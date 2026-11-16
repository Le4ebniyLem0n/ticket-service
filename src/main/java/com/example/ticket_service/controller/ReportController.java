package com.example.ticket_service.controller;

import com.example.ticket_service.report.TicketReportService;
import com.example.ticket_service.report.TicketSummaryReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Аналитика", description = "Сводная статистика по заявкам (модуль на Kotlin)")
public class ReportController {

    private final TicketReportService reportService;

    public ReportController(TicketReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Получить сводную статистику за период [from, to)")
    public TicketSummaryReport getSummary(
            @Parameter(description = "Начало периода (включительно) в формате ISO-8601", example = "2026-11-01T00:00:00Z")
            @RequestParam Instant from,
            @Parameter(description = "Конец периода (исключительно) в формате ISO-8601", example = "2026-12-01T00:00:00Z")
            @RequestParam Instant to
    ) {
        return reportService.getSummary(from, to);
    }
}
