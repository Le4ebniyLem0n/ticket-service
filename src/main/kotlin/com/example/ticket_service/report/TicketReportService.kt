package com.example.ticket_service.report

import com.example.ticket_service.model.TicketPriority
import com.example.ticket_service.model.TicketStatus
import com.example.ticket_service.repository.TicketRepository
import com.example.ticket_service.service.TicketService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class TicketReportService(
    private val ticketRepository: TicketRepository,
    private val ticketService: TicketService,
    private val clock: Clock
) {

    @Transactional(readOnly = true)
    fun getSummary(from: Instant, to: Instant): TicketSummaryReport {
        require(from.isBefore(to)) {
            "Параметр 'from' должен быть строго меньше параметра 'to'"
        }

        val tickets = ticketRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        val now = Instant.now(clock)

        val rawStatusCounts = tickets.groupingBy { it.status }.eachCount()
        val byStatus = TicketStatus.entries.associateWith { status ->
            rawStatusCounts[status] ?: 0
        }

        val rawPriorityCounts = tickets.groupingBy { it.priority }.eachCount()
        val byPriority = TicketPriority.entries.associateWith { priority ->
            rawPriorityCounts[priority] ?: 0
        }

        val overdueOpenCount = tickets.count { ticket ->
            ticketService.isOverdue(ticket, now)
        }

        val resolutionDurations = tickets
            .filter { it.status == TicketStatus.RESOLVED && it.closedAt != null }
            .map { Duration.between(it.createdAt, it.closedAt).toSeconds() }

        val averageResolutionSeconds: Double? = if (resolutionDurations.isEmpty()) {
            null
        } else {
            resolutionDurations.average()
        }

        return TicketSummaryReport(
            from = from,
            to = to,
            totalCreated = tickets.size,
            byStatus = byStatus,
            byPriority = byPriority,
            overdueOpenCount = overdueOpenCount,
            averageResolutionSeconds = averageResolutionSeconds
        )
    }
}
