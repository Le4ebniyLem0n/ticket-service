package com.example.ticket_service.repository;

import com.example.ticket_service.model.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {

    List<TicketStatusHistory> findAllByTicketIdOrderByChangedAtAsc(Long ticketId);
}
