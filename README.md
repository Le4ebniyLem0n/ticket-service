# Ticket Service — Сервис обработки заявок

REST API для регистрации, обработки заявок на техподдержку и получения сводной статистики.

## Стек и версии инструментов
* **Языки:** Java 21, Kotlin 2.3.0
* **Фреймворк:** Spring Boot 4.1.2 (Web, Data JPA, Validation, Actuator)
* **База данных:** PostgreSQL 16, миграции — Flyway
* **Документация API:** OpenAPI 3 / Swagger UI (`springdoc-openapi` 3.0.3)
* **Сборка и тестирование:** Gradle (Wrapper), JUnit 5, Mockito, MockK, Testcontainers, JaCoCo
* **Инфраструктура:** Docker, Docker Compose, GitHub Actions

## Структура проекта
* `docs/` — спецификация требований (`specification.md`), ER-диаграмма и схема статусов (`diagrams/`).
* `src/main/java` — основной REST API, работа с БД и жизненным циклом заявок.
* `src/main/kotlin` — модуль расчёта сводной статистики (`GET /api/reports/summary`).
* `src/main/resources/db/migration` — SQL-миграции Flyway.