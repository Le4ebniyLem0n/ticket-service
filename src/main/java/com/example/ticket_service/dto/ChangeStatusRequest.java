package com.example.ticket_service.dto;

import com.example.ticket_service.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
        @NotNull(message = "Необходимо указать новый статус")
        TicketStatus status,

        @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
        String comment
) {
}
