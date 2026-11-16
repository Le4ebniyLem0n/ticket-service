package com.example.ticket_service.dto;

import com.example.ticket_service.model.TicketPriority;
import java.time.Instant;

public record UpdateTicketRequest(
        String description,
        TicketPriority priority,
        Instant dueDate
) {
}
