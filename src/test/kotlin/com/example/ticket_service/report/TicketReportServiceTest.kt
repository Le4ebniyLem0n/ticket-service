package com.example.ticket_service.report

import com.example.ticket_service.exception.TicketStateConflictException
import com.example.ticket_service.model.*
import com.example.ticket_service.repository.CategoryRepository
import com.example.ticket_service.repository.TicketRepository
import com.example.ticket_service.repository.TicketStatusHistoryRepository
import com.example.ticket_service.service.TicketService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

class TicketReportServiceTest {

    private val fixedNow = Instant.parse("2026-11-16T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private val ticketRepository = mockk<TicketRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val historyRepository = mockk<TicketStatusHistoryRepository>()
    private val javaTicketService = TicketService(ticketRepository, categoryRepository, historyRepository, fixedClock)
    private val reportService = TicketReportService(ticketRepository, javaTicketService, fixedClock)

    @Test
    fun `getSummary calculates counts overdue and average resolution time`() {
        val from = fixedNow.minus(10, ChronoUnit.DAYS)
        val to = fixedNow.plus(1, ChronoUnit.DAYS)

        val resolvedTicket = buildTicket(
            status = TicketStatus.RESOLVED,
            priority = TicketPriority.HIGH,
            createdAt = fixedNow.minus(2, ChronoUnit.HOURS),
            closedAt = fixedNow.minus(1, ChronoUnit.HOURS) // 3600 секунд
        )
        val overdueTicket = buildTicket(
            status = TicketStatus.NEW,
            priority = TicketPriority.LOW,
            createdAt = fixedNow.minus(3, ChronoUnit.HOURS),
            dueDate = fixedNow.minus(10, ChronoUnit.MINUTES)
        )

        every {
            ticketRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        } returns listOf(resolvedTicket, overdueTicket)

        val summary = reportService.getSummary(from, to)

        assertEquals(2, summary.totalCreated)
        assertEquals(1, summary.byStatus[TicketStatus.RESOLVED])
        assertEquals(1, summary.byStatus[TicketStatus.NEW])
        assertEquals(0, summary.byStatus[TicketStatus.IN_PROGRESS])
        assertEquals(1, summary.overdueOpenCount)
        assertEquals(3600.0, summary.averageResolutionSeconds)
    }

    @Test
    fun `getSummary returns null averageResolutionSeconds when no resolved tickets`() {
        val from = fixedNow.minus(5, ChronoUnit.DAYS)
        val to = fixedNow

        val openTicket = buildTicket(status = TicketStatus.IN_PROGRESS, priority = TicketPriority.MEDIUM)
        every {
            ticketRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        } returns listOf(openTicket)

        val summary = reportService.getSummary(from, to)

        assertEquals(1, summary.totalCreated)
        assertNull(summary.averageResolutionSeconds)
    }

    @Test
    fun `getSummary throws IllegalArgumentException when from is not before to`() {
        assertThrows(IllegalArgumentException::class.java) {
            reportService.getSummary(fixedNow, fixedNow)
        }
    }

    @Test
    fun `kotlin test verifies java TicketService rejects editing cancelled ticket`() {
        val cancelled = buildTicket(status = TicketStatus.CANCELLED, priority = TicketPriority.HIGH)
        every { ticketRepository.findById(42L) } returns Optional.of(cancelled)

        assertThrows(TicketStateConflictException::class.java) {
            javaTicketService.getTicketById(42L) // проверка чтения проходит
            javaTicketService.changeStatus(
                42L,
                com.example.ticket_service.dto.ChangeStatusRequest(TicketStatus.NEW, "Попытка из Kotlin")
            )
        }
    }

    private fun buildTicket(
        status: TicketStatus,
        priority: TicketPriority,
        createdAt: Instant = fixedNow.minus(1, ChronoUnit.HOURS),
        dueDate: Instant? = null,
        closedAt: Instant? = null
    ): Ticket = Ticket().apply {
        this.title = "Заявка"
        this.category = Category("Сеть", "Описание")
        this.status = status
        this.priority = priority
        this.createdAt = createdAt
        this.dueDate = dueDate
        this.closedAt = closedAt
    }
}
