package com.example.ticket_service.report

import com.example.ticket_service.model.TicketPriority
import com.example.ticket_service.model.TicketStatus
import java.time.Instant

data class TicketSummaryReport(
    val from: Instant,
    val to: Instant,
    val totalCreated: Int,
    val byStatus: Map<TicketStatus, Int>,
    val byPriority: Map<TicketPriority, Int>,
    val overdueOpenCount: Int,
    val averageResolutionSeconds: Double?
)
