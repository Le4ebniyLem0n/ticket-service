package com.example.ticket_service.dto;

import com.example.ticket_service.model.Category;

public record CategoryResponse(
        Long id,
        String name,
        String description
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}
