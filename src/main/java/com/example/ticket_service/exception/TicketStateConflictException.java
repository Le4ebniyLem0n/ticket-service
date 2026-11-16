package com.example.ticket_service.exception;

public class TicketStateConflictException extends RuntimeException {
    public TicketStateConflictException(String message) {
        super(message);
    }
}
