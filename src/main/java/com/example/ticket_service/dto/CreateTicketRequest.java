package com.example.ticket_service.dto;

import com.example.ticket_service.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateTicketRequest(
        @NotBlank(message = "Заголовок заявки обязателен")
        @Size(max = 255, message = "Заголовок не должен превышать 255 символов")
        String title,

        String description,

        @NotNull(message = "Необходимо указать ID категории")
        Long categoryId,

        @NotNull(message = "Необходимо указать приоритет (LOW, MEDIUM, HIGH)")
        TicketPriority priority,

        Instant dueDate
) {
}
