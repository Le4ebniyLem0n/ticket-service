# Ticket Service — Сервис обработки заявок

REST API для регистрации, отслеживания жизненного цикла заявок на техническую поддержку и формирования сводной статистики.

## Стек и версии инструментов
* **Языки:** Java 21 (Eclipse Temurin), Kotlin 2.3.0
* **Фреймворк:** Spring Boot 4.1.2 (Web, Data JPA, Validation, Actuator)
* **База данных:** PostgreSQL 16, миграции — Flyway
* **Документация API:** OpenAPI 3 / Swagger UI (`springdoc-openapi` 3.0.3)
* **Сборка и тестирование:** Gradle (Wrapper), JUnit 5, Mockito, MockK, Testcontainers, JaCoCo
* **Инфраструктура:** Docker, Docker Compose, GitHub Actions (`ghcr.io`)

## Быстрый запуск через Docker Compose
1. Создайте `.env` из шаблона:
   ```powershell
   copy .env.example .env
   ```
2. Запустите сервисы:
   ```powershell
   docker compose up -d --build
   ```
3. Откройте документацию Swagger UI: `http://localhost:8080/swagger-ui.html`
4. Проверка работоспособности: `http://localhost:8080/actuator/health`

## Откат к предыдущей версии приложения
Каждый успешный запуск CD-пайплайна публикует образ с тегом коммита `ghcr.io/le4ebniylem0n/ticket-service:sha-<commit>`.
Для отката достаточно указать нужный тег предыдущей стабильной версии в `.env` (`IMAGE_TAG=sha-xxxxxxx`) и выполнить:
```powershell
docker compose pull app
docker compose up -d app
```

## Документация проекта
* `docs/specification.md` — спецификация требований, пользовательские истории, контракт API и backlog.
* `docs/diagrams/` — ER-диаграмма БД и диаграмма жизненного цикла заявки.
* `docs/test-plan.md` — тест-план, журнал дефектов и отчёт о покрытии JaCoCo.
* `docs/operations.md` — инструкция по эксплуатации, логам и резервному копированию PostgreSQL.
* `docs/postman_collection.json` — коллекция HTTP-запросов для Postman.
