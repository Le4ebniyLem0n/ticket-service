package com.example.ticket_service.dto;

import com.example.ticket_service.model.TicketStatus;
import com.example.ticket_service.model.TicketStatusHistory;
import java.time.Instant;

public record TicketHistoryResponse(
        Long id,
        TicketStatus oldStatus,
        TicketStatus newStatus,
        Instant changedAt,
        String comment
) {
    public static TicketHistoryResponse from(TicketStatusHistory history) {
        return new TicketHistoryResponse(
                history.getId(),
                history.getOldStatus(),
                history.getNewStatus(),
                history.getChangedAt(),
                history.getComment()
        );
    }
}
