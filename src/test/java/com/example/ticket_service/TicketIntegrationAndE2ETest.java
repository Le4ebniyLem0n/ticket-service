package com.example.ticket_service;

import com.example.ticket_service.dto.ChangeStatusRequest;
import com.example.ticket_service.dto.CreateTicketRequest;
import com.example.ticket_service.dto.UpdateTicketRequest;
import com.example.ticket_service.model.TicketPriority;
import com.example.ticket_service.model.TicketStatus;
import com.example.ticket_service.repository.TicketRepository;
import com.example.ticket_service.repository.TicketStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketIntegrationAndE2ETest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketStatusHistoryRepository historyRepository;

    @BeforeEach
    void cleanDb() {
        historyRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    // --- ИНТЕГРАЦИОННЫЕ ТЕСТЫ (1..6) ---

    @Test
    void integration1_flywaySeedCategoriesLoaded() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name").value("Оборудование"));
    }

    @Test
    void integration2_createTicketWithBlankTitle_returnsUnified400() throws Exception {
        CreateTicketRequest invalid = new CreateTicketRequest("   ", "Описание", 1L, TicketPriority.HIGH, null);

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/tickets"));
    }

    @Test
    void integration3_getNonExistentTicket_returnsUnified404() throws Exception {
        mockMvc.perform(get("/api/tickets/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void integration4_updateOpenTicket_updatesFieldsInPostgres() throws Exception {
        Long id = createSampleTicket("Начальный заголовок", TicketPriority.LOW, null);

        UpdateTicketRequest update = new UpdateTicketRequest("Новое описание", TicketPriority.HIGH, null);

        mockMvc.perform(patch("/api/tickets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Новое описание"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void integration5_searchTicketsByStatusAndOverdueFilter() throws Exception {
        createSampleTicket("Просроченная", TicketPriority.HIGH, Instant.now().minus(2, ChronoUnit.DAYS));
        createSampleTicket("Актуальная", TicketPriority.LOW, Instant.now().plus(2, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/tickets")
                        .param("overdue", "true")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Просроченная"));
    }

    @Test
    void integration6_kotlinReportEndpoint_returnsSummary() throws Exception {
        createSampleTicket("Заявка для отчёта", TicketPriority.MEDIUM, null);

        String from = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String to = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        mockMvc.perform(get("/api/reports/summary")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreated").value(1))
                .andExpect(jsonPath("$.byStatus.NEW").value(1))
                .andExpect(jsonPath("$.averageResolutionSeconds").value(nullValue()));
    }

    // --- СКВОЗНЫЕ E2E СЦЕНАРИИ (1..2) ---

    @Test
    void e2e1_fullTicketLifecycleAndStatistics() throws Exception {
        // 1. Создание заявки
        Long ticketId = createSampleTicket("Настройка VPN", TicketPriority.HIGH, Instant.now().plus(1, ChronoUnit.DAYS));

        // 2. Перевод в работу
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest(TicketStatus.IN_PROGRESS, "Взял в работу"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 3. Закрытие заявки
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest(TicketStatus.RESOLVED, "Проблема решена"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        // 4. Проверка полной истории (3 записи: null->NEW, NEW->IN_PROGRESS, IN_PROGRESS->RESOLVED)
        mockMvc.perform(get("/api/tickets/" + ticketId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].newStatus").value("RESOLVED"));

        // 5. Проверка попадания в сводный отчёт Kotlin
        String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        mockMvc.perform(get("/api/reports/summary").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
                .andExpect(jsonPath("$.averageResolutionSeconds").isNumber());
    }

    @Test
    void e2e2_forbiddenTransitionOnClosedTicket_keepsDbAndHistoryUnchanged() throws Exception {
        Long ticketId = createSampleTicket("Закрываемая заявка", TicketPriority.LOW, null);

        // Переводим NEW -> CANCELLED
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest(TicketStatus.CANCELLED, "Отмена"))))
                .andExpect(status().isOk());

        long historyCountBefore = historyRepository.count();

        // Пытаемся повторно открыть CANCELLED -> IN_PROGRESS (ожидаем 409)
        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest(TicketStatus.IN_PROGRESS, "Попытка открыть"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Проверяем, что статус в БД остался CANCELLED, а лишняя запись в историю не добавилась
        assertEquals(TicketStatus.CANCELLED, ticketRepository.findById(ticketId).orElseThrow().getStatus());
        assertEquals(historyCountBefore, historyRepository.count());
    }

    private Long createSampleTicket(String title, TicketPriority priority, Instant dueDate) throws Exception {
        CreateTicketRequest req = new CreateTicketRequest(title, "Тестовое описание", 1L, priority, dueDate);
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
