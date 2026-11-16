package com.example.ticket_service.controller;

import com.example.ticket_service.dto.*;
import com.example.ticket_service.model.TicketPriority;
import com.example.ticket_service.model.TicketStatus;
import com.example.ticket_service.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Заявки", description = "Управление жизненным циклом заявок на поддержку")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать новую заявку")
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить заявку по ID")
    public TicketResponse getById(@PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Редактировать описание, приоритет и срок заявки")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.updateTicket(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Изменить статус заявки")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return ticketService.changeStatus(id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Получить историю изменения статусов заявки")
    public List<TicketHistoryResponse> getHistory(@PathVariable Long id) {
        return ticketService.getTicketHistory(id);
    }

    @GetMapping
    @Operation(summary = "Поиск заявок по фильтрам с пагинацией")
    public Page<TicketResponse> search(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ticketService.searchTickets(status, priority, categoryId, overdue, pageable);
    }
}
