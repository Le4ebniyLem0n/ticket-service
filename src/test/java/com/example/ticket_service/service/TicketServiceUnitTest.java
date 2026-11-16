package com.example.ticket_service.service;

import com.example.ticket_service.dto.ChangeStatusRequest;
import com.example.ticket_service.dto.CreateTicketRequest;
import com.example.ticket_service.dto.TicketResponse;
import com.example.ticket_service.dto.UpdateTicketRequest;
import com.example.ticket_service.exception.ResourceNotFoundException;
import com.example.ticket_service.exception.TicketStateConflictException;
import com.example.ticket_service.model.*;
import com.example.ticket_service.repository.CategoryRepository;
import com.example.ticket_service.repository.TicketRepository;
import com.example.ticket_service.repository.TicketStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceUnitTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TicketStatusHistoryRepository historyRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        ticketService = new TicketService(ticketRepository, categoryRepository, historyRepository, fixedClock);
    }

    @Test
    void createTicket_validRequest_savesTicketAndInitialHistory() {
        Category category = new Category("Оборудование", "Описание");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest(
                "Сломался монитор",
                "Не включается",
                1L,
                TicketPriority.HIGH,
                FIXED_NOW.plus(2, ChronoUnit.DAYS)
        );

        TicketResponse response = ticketService.createTicket(request);

        assertEquals("Сломался монитор", response.title());
        assertEquals(TicketStatus.NEW, response.status());
        assertEquals(FIXED_NOW, response.createdAt());
        assertFalse(response.overdue());

        ArgumentCaptor<TicketStatusHistory> historyCaptor = ArgumentCaptor.forClass(TicketStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertNull(historyCaptor.getValue().getOldStatus());
        assertEquals(TicketStatus.NEW, historyCaptor.getValue().getNewStatus());
    }

    @Test
    void createTicket_categoryNotFound_throws404() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        CreateTicketRequest request = new CreateTicketRequest("Заголовок", null, 99L, TicketPriority.LOW, null);

        assertThrows(ResourceNotFoundException.class, () -> ticketService.createTicket(request));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_terminalStatus_throwsConflict409() {
        Ticket ticket = createTicketWithStatus(TicketStatus.RESOLVED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest("Новое описание", TicketPriority.LOW, null);

        assertThrows(TicketStateConflictException.class, () -> ticketService.updateTicket(1L, request));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void changeStatus_validTransitionToResolved_setsClosedAtAndWritesHistory() {
        Ticket ticket = createTicketWithStatus(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.changeStatus(1L, new ChangeStatusRequest(TicketStatus.RESOLVED, "Готово"));

        assertEquals(TicketStatus.RESOLVED, response.status());
        assertEquals(FIXED_NOW, response.closedAt());
        verify(historyRepository).save(any(TicketStatusHistory.class));
    }

    @Test
    void changeStatus_forbiddenTransitionFromResolved_throwsConflict409() {
        Ticket ticket = createTicketWithStatus(TicketStatus.RESOLVED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThrows(TicketStateConflictException.class,
                () -> ticketService.changeStatus(1L, new ChangeStatusRequest(TicketStatus.IN_PROGRESS, "Повторно")));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void isOverdue_openTicketPastDueDate_returnsTrue() {
        Ticket ticket = createTicketWithStatus(TicketStatus.IN_PROGRESS);
        ticket.setDueDate(FIXED_NOW.minus(1, ChronoUnit.HOURS));

        assertTrue(ticketService.isOverdue(ticket, FIXED_NOW));
    }

    @Test
    void isOverdue_resolvedTicketPastDueDate_returnsFalse() {
        Ticket ticket = createTicketWithStatus(TicketStatus.RESOLVED);
        ticket.setDueDate(FIXED_NOW.minus(5, ChronoUnit.HOURS));

        assertFalse(ticketService.isOverdue(ticket, FIXED_NOW));
    }

    private Ticket createTicketWithStatus(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setTitle("Тестовая заявка");
        ticket.setCategory(new Category("ПО", "Описание"));
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(status);
        ticket.setCreatedAt(FIXED_NOW.minus(2, ChronoUnit.DAYS));
        return ticket;
    }
}
