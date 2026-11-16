package com.example.ticket_service.service;

import com.example.ticket_service.dto.*;
import com.example.ticket_service.exception.ResourceNotFoundException;
import com.example.ticket_service.exception.TicketStateConflictException;
import com.example.ticket_service.model.*;
import com.example.ticket_service.repository.CategoryRepository;
import com.example.ticket_service.repository.TicketRepository;
import com.example.ticket_service.repository.TicketStatusHistoryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TicketService {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.NEW, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED),
            TicketStatus.RESOLVED, Set.of(),
            TicketStatus.CANCELLED, Set.of()
    );

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         CategoryRepository categoryRepository,
                         TicketStatusHistoryRepository historyRepository,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Категория с id=" + request.categoryId() + " не найдена"));

        Instant now = Instant.now(clock);

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setCategory(category);
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setCreatedAt(now);
        ticket.setDueDate(request.dueDate());

        Ticket saved = ticketRepository.save(ticket);

        TicketStatusHistory initialHistory = new TicketStatusHistory(
                saved,
                null,
                TicketStatus.NEW,
                now,
                "Заявка создана"
        );
        historyRepository.save(initialHistory);

        return toResponse(saved, now);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = findTicketOrThrow(id);
        return toResponse(ticket, Instant.now(clock));
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = findTicketOrThrow(id);

        if (isTerminalStatus(ticket.getStatus())) {
            throw new TicketStateConflictException(
                    "Нельзя редактировать заявку в статусе " + ticket.getStatus()
            );
        }

        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }

        return toResponse(ticketRepository.save(ticket), Instant.now(clock));
    }

    @Transactional
    public TicketResponse changeStatus(Long id, ChangeStatusRequest request) {
        Ticket ticket = findTicketOrThrow(id);
        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = request.status();

        Set<TicketStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedNext.contains(targetStatus)) {
            throw new TicketStateConflictException(
                    "Недопустимый переход статуса из " + currentStatus + " в " + targetStatus
            );
        }

        Instant now = Instant.now(clock);
        ticket.setStatus(targetStatus);
        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setClosedAt(now);
        }

        Ticket updated = ticketRepository.save(ticket);

        TicketStatusHistory historyEntry = new TicketStatusHistory(
                updated,
                currentStatus,
                targetStatus,
                now,
                request.comment()
        );
        historyRepository.save(historyEntry);

        return toResponse(updated, now);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> getTicketHistory(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Заявка с id=" + id + " не найдена");
        }
        return historyRepository.findAllByTicketIdOrderByChangedAtAsc(id)
                .stream()
                .map(TicketHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> searchTickets(TicketStatus status,
                                              TicketPriority priority,
                                              Long categoryId,
                                              Boolean overdue,
                                              Pageable pageable) {
        Instant now = Instant.now(clock);

        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (overdue != null) {
                Predicate isOpen = root.get("status").in(TicketStatus.NEW, TicketStatus.IN_PROGRESS);
                Predicate isExpired = cb.and(
                        cb.isNotNull(root.get("dueDate")),
                        cb.lessThan(root.get("dueDate"), now)
                );
                Predicate overduePredicate = cb.and(isOpen, isExpired);

                if (overdue) {
                    predicates.add(overduePredicate);
                } else {
                    predicates.add(cb.not(overduePredicate));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec, pageable)
                .map(ticket -> toResponse(ticket, now));
    }

    public boolean isOverdue(Ticket ticket, Instant now) {
        if (isTerminalStatus(ticket.getStatus()) || ticket.getDueDate() == null) {
            return false;
        }
        return now.isAfter(ticket.getDueDate());
    }

    private boolean isTerminalStatus(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CANCELLED;
    }

    private Ticket findTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Заявка с id=" + id + " не найдена"));
    }

    private TicketResponse toResponse(Ticket ticket, Instant now) {
        return TicketResponse.from(ticket, isOverdue(ticket, now));
    }
}
