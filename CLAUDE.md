# CLAUDE.md

Guidance for Claude Code when working in this repository. User-facing docs (features, config) are in `README.md` — keep it updated with every feature change.

## Build & Run

```bash
mvn clean install                       # build + tests (Docker required: TestContainers PostgreSQL 18)
mvn test -Dtest="InventoryServiceTest"  # single class
# Windows / Rancher Desktop: DOCKER_HOST=npipe:////./pipe/docker_engine mvn test
docker compose up -d db mailpit         # local infra: PostgreSQL :5435, Mailpit SMTP :1027, UI :8027
```

## Architecture

Spring Boot 4.1 / Java 25, Thymeleaf SSR (Italian UI, no i18n), PostgreSQL + Flyway, form login.

```
Controller (thin; DTOs/forms/ids only) → Service (@Transactional, entity→DTO inside tx) → Repository → PostgreSQL
```

- `open-in-view: false`; controller-facing reads are `*View*` methods returning DTOs. `ControllerBoundaryTest` (ArchUnit) fails the build if a controller depends on `..entity..` or `..repository..`.
- `BusinessException` = user-facing rule violation (Italian message): controllers catch it and show it (flash `error` or JSON `{error}`). `AccessDeniedException` → 403, `NotFoundException` → 404.
- Quantity-changing JSON endpoints (`/publications/{id}/quantity`, `/inventory/{id}/count`) are called by `static/js/app.js` with the CSRF header from the layout meta tags.

### Roles & authorization

- Authorities are hierarchical (`CustomUserDetailsService.authoritiesFor`): SUPER_ADMIN ⊃ ADMIN ⊃ USER.
- `SecurityConfig` does path rules and ends with `anyRequest().denyAll()` — new routes must be registered there.
- Data-dependent rules live in services: `UserService` (ADMIN scoped to own congregation, SuperAdmin untouchable, no SUPER_ADMIN assignment), `NotificationService` (scope ADMIN = shared among admins, read-only for users; scope USER = private to owner).
- The single SuperAdmin comes from `app.super-admin.*` (`SuperAdminBootstrap`, realigned on every start); DB partial unique index + check constraint (`congregation_id IS NULL` iff SUPER_ADMIN).

### Domain

- **Publication** (code unique case-insensitive, title, quantity ≥ 0); image bytes in `publication_images` (separate table so lists never load them), type detected from magic bytes, max 2 MB.
- **StockMovement** — append-only log (IN/OUT/SET/INVENTORY) with user/congregation *name snapshots*. All quantity changes go through `MovementService.applyQuantity` on a row-locked publication (`findByIdForUpdate`).
- **InventorySession/InventoryItem** — one OPEN session at a time (partial unique index). Counts saved per publication; `close` sets `expected_quantity` from the locked current quantity and applies counted values. Uncounted publications untouched. Cancel = delete.
- **ScheduledNotification** — `ScheduleRule` (ONCE/DAILY/WEEKLY/MONTHLY, monthly day clamped to month length) computes `next_run_at`; `NotificationScheduler` polls `dispatchDue()` (rows locked `FOR UPDATE`). Failed sends set `last_error`, no retry. Timestamps are `LocalDateTime` in `app.timezone` via the `Clock` bean.

## Testing

- Tests extend `BaseTest` (real PostgreSQL via `PostgresTestContainerConfig`; `JavaMailSender` is a `@MockitoBean`, stub with `doReturn().when()`). Use `@Transactional` for rollback.
- The SuperAdmin `superadmin@test.com` is bootstrapped: fetch it with `superAdmin()`, never recreate it.
- `PageRenderingTest` renders every template with data — add new pages there.
- Scheduler is disabled in tests (`app.notifications.scheduler-enabled: false`); call `dispatchDue()` directly.

## Conventions

- Lombok `@RequiredArgsConstructor`, `@Slf4j`, `@Builder` on entities; no MapStruct (manual mapping in services).
- Flyway migrations in `src/main/resources/db/migration`; never edit an applied migration, add `V2+`.
- Templates: `fragments/layout.html` provides `head(title)`, `navbar(active)`, `alerts`, `scripts`. Don't combine `th:each` and `th:if` on the same tag (th:each runs first).
