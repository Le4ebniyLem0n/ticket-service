package com.example.ticket_service.dto;

import com.example.ticket_service.model.Ticket;
import com.example.ticket_service.model.TicketPriority;
import com.example.ticket_service.model.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        Long id,
        String title,
        String description,
        CategoryShortDto category,
        TicketPriority priority,
        TicketStatus status,
        Instant createdAt,
        Instant dueDate,
        Instant closedAt,
        boolean overdue
) {
    public record CategoryShortDto(Long id, String name) {
    }

    public static TicketResponse from(Ticket ticket, boolean overdue) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                new CategoryShortDto(ticket.getCategory().getId(), ticket.getCategory().getName()),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getDueDate(),
                ticket.getClosedAt(),
                overdue
        );
    }
}
