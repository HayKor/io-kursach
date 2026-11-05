# План реализации сервера СУДВ

Документ для агентов-исполнителей. Выполняется **по фазам, строго по порядку**. После каждой фазы работу принимает судья. Следующая фаза начинается только после приёмки предыдущей.

## 0. Правила для исполнителя

1. Перед началом прочитай `AGENTS.md` целиком и раздел своей фазы в этом плане. Остальные фазы читай только для контекста.
2. Делай **только свою фазу**. Код следующих фаз не пиши, даже если «заодно».
3. **Не меняй `shared:contract`**, кроме явно разрешённых в фазе правок. Если контракт мешает, остановись и опиши проблему в отчёте.
4. Комментарии в коде пишутся **только по делу и только на английском**. Не пиши комментарии, которые пересказывают имя функции. Сообщения об ошибках для пользователя (`ErrorResponse.message`) пишутся **на русском**.
5. Идентификаторы — только value class из `shared:domain` (`EmployeeId` и т. д.), голый `Long` для id не используется. С БД — только Exposed DSL через R2DBC и `suspendTransaction`. Блокирующий `transaction {}`, JDBC и DAO не используются.
6. Не отключай, не пропускай и не ослабляй тесты. Тест, который ничего не проверяет, считается невыполненной работой.
7. Перед сдачей фазы должны проходить (Docker запущен):
   ```bash
   ./kotlin build
   ./kotlin test
   ```
8. Не коммить. В конце фазы напиши отчёт: какие файлы созданы или изменены, что проверено, где отступил от плана и почему.

## 1. Принятые решения

| Вопрос | Решение | Почему |
|---|---|---|
| Схема БД | `SchemaUtils.create(...)` при старте сервера по объектам таблиц Exposed | Таблицы описаны один раз, новых зависимостей нет |
| Тесты | API-тесты на настоящем PostgreSQL 16 через Testcontainers | Проверяются SQL, ограничения и права целиком |
| Пароли | PBKDF2-HMAC-SHA256 из JDK, соль 16 байт, 600 000 итераций | Без сторонних библиотек, параметры по рекомендации OWASP |
| Токен | JWT (HMAC256) из `ktor-server-auth-jwt`, `sub` = id пользователя, срок 12 ч | Рабочая смена. Роль и блокировка читаются из БД при каждом запросе, поэтому блокировка действует сразу |
| Права | Проверка роли — в маршрутах («Веб-API»), ограничение подразделения для MANAGER — в сервисах | Так описано в отчёте, гл. 06: проверка прав — «Веб-API» и «Управление доступом» |
| Начальные данные | Подразделения из `application.yaml`; первый ADMIN из env `ADMIN_LOGIN`/`ADMIN_PASSWORD`, если в БД нет ни одного ADMIN | В API нет создания подразделений, а учётная запись обязана ссылаться на сотрудника |
| XLSX | `org.dhatim:fastexcel` | Лёгкая библиотека, умеет только запись. Apache POI избыточен |
| CSV | UTF-8 с BOM, разделитель `;`, CRLF, кавычки по RFC 4180 | Русский Excel открывает такой CSV без настройки |
| Погашение | Ежедневная корутина в приложении, плюс прогон при старте | Планировщик без внешних зависимостей. Прогон при старте догоняет пропущенные дни |
| Юрисконсульт | Роль `HR` | Ролей остаётся три, отчёт не меняется |

### Матрица прав

| Действие | ADMIN | HR | MANAGER |
|---|---|---|---|
| Учётные записи: список, создание, блокировка | ✔ | — | — |
| Подразделения: список | ✔ | ✔ | ✔ |
| Сотрудники: просмотр | ✔ | ✔ | своё подр. |
| Сотрудники: создание | ✔ | ✔ | — |
| Сотрудники: редактирование, архив | — | ✔ | — |
| Реестр, карточка взыскания, экспорт реестра | — | ✔ | своё подр. |
| Регистрация взыскания, досрочное снятие | — | ✔ | — |
| Вложения: загрузка, удаление | — | ✔ | — |
| Вложения: скачивание | — | ✔ | своё подр. |
| Персональная карточка и её экспорт | — | ✔ | своё подр. |
| Сводный отчёт, архив отчётов | — | ✔ | — |

«Своё подр.» — подразделение сотрудника, к которому привязана учётная запись MANAGER. Обращение к данным чужого подразделения → `FORBIDDEN`. Роль без права на действие → `FORBIDDEN`.

## 2. Архитектура сервера

Компоненты соответствуют диаграмме компонентов из отчёта (гл. 06). **Имена интерфейсов берутся из отчёта, с префиксом `I`**: на защите их будут сверять с диаграммой.

Структура `server/src/`. Папка соответствует подпакету, корневой пакет `ru.haykor.sudv`:

```
server/src/
  main.kt                 ru.haykor.sudv          main(), Application.module()
  AppGraph.kt             ru.haykor.sudv          Metro graph
  Swagger.kt              ru.haykor.sudv
  config/                 ru.haykor.sudv.config   AppConfig (typed config), Bootstrap
  api/                    ru.haykor.sudv.api      «Веб-API»: routes, StatusPages, auth plugin, role checks, param parsing
  auth/                   ru.haykor.sudv.auth     «Управление доступом»: IAuthService, PasswordHasher, JwtService, CurrentUser
  records/                ru.haykor.sudv.records  «Учёт сотрудников и нарушений»: IRecordService
  expiry/                 ru.haykor.sudv.expiry   «Контроль сроков» + «Планировщик заданий»: IExpiryService, ExpiryScheduler
  reports/                ru.haykor.sudv.reports  «Отчётность»: IReportService, CsvWriter, XlsxWriter
  files/                  ru.haykor.sudv.files    «Хранилище файлов»: IFileStorage
  db/                     ru.haykor.sudv.db       «Доступ к данным»: tables, I*Repository + impls, Transactor
server/test/              ru.haykor.sudv          тесты; общий харнес в TestApp.kt
```

DI выполняется через Metro. Реализация помечается `@Inject @SingleIn(AppScope::class) @ContributesBinding(AppScope::class) class X(...) : IX`. Граф отдаёт наружу то, что нужно маршрутам, планировщику и bootstrap.

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val authService: IAuthService
    val recordService: IRecordService
    val expiryService: IExpiryService
    val reportService: IReportService
    val bootstrap: Bootstrap
    val database: R2dbcDatabase

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides config: ApplicationConfig, @Provides clock: Clock): AppGraph
    }
}
```

`Clock` — это `kotlin.time.Clock`. В `main` передаётся `Clock.System`, в тестах тоже `Clock.System`: погашение тестируется вызовом `expireDue(today)` с явной датой. `TimeZone` и `AppConfig` предоставляются `@Provides`-функциями графа из `ApplicationConfig`.

### Транзакции

`class Transactor(db: R2dbcDatabase) { suspend fun <T> tx(block: suspend () -> T): T = suspendTransaction(db) { block() } }`.

- Каждый метод репозитория выполняется внутри `transactor.tx { }`.
- Сервис оборачивает в `transactor.tx { }` любую операцию из нескольких записей: создание взыскания вместе с событием, досрочное снятие, создание отчёта, bootstrap. Вложенный вызов присоединяется к внешней транзакции.
- **Проверка в фазе 2:** тест на то, что вложенный `tx` действительно присоединяется. Внешний `tx` пишет строку и бросает исключение, после чего строки в БД нет.

## 3. Конфигурация

Дополнения к `server/resources/application.yaml`:

```yaml
app:
    timeZone: "$APP_TIME_ZONE:Europe/Moscow"

security:
    jwt:
        secret: "$JWT_SECRET:dev-only-secret-change-me"
        issuer: "sudv"
        ttlHours: 12
    passwordHashIterations: 600000

files:
    dir: "$FILES_DIR:build/files"
    # Maximum attachment size, bytes (10 MiB).
    maxAttachmentBytes: 10485760

expiry:
    schedulerEnabled: true
    # Local time of the daily run, HH:MM in app.timeZone.
    runAt: "00:05"

bootstrap:
    departments:
        - "Дирекция"
        - "Отдел кадров"
        - "Юридический отдел"
        - "ИТ-отдел"
        - "Бухгалтерия"
        - "Производственные подразделения"
    admin:
        login: "$ADMIN_LOGIN:admin"
        password: "$ADMIN_PASSWORD:admin12345"
        department: "ИТ-отдел"
```

`AppConfig` — data class с типизированными полями, собирается из `ApplicationConfig` в одной функции. Сервисы получают `AppConfig`, а не `ApplicationConfig`.

## 4. Правила предметной области (`shared:domain`)

В `shared/domain/module.yaml` добавить зависимость `$libs.kotlinx.datetime: exported`. Пакет `ru.haykor.sudv.domain`. Все правила — чистые функции без ввода-вывода.

```kotlin
object PenaltyRules {
    fun expiryDate(orderDate: LocalDate): LocalDate            // orderDate.plus(1, DateTimeUnit.YEAR)
    fun isDue(orderDate: LocalDate, today: LocalDate): Boolean // today >= expiryDate(orderDate)
    fun remainingDays(orderDate: LocalDate, today: LocalDate): Int // max(0, days until expiryDate)
    fun canRemoveEarly(status: PenaltyStatus): Boolean         // status == ACTIVE
}

object AttachmentRules {
    const val MAX_BYTES: Long = 10L * 1024 * 1024
    val allowedContentTypes: Set<String> = setOf("application/pdf", "image/jpeg")
    fun matchesSignature(contentType: String, bytes: ByteArray): Boolean // PDF: "%PDF", JPEG: FF D8 FF
}

val PenaltyType.displayName: String     // "Замечание", "Выговор"
val PenaltyStatus.displayName: String   // "Действует", "Погашено", "Снято досрочно"
val UserRole.displayName: String        // "Администратор", "Кадровик", "Руководитель"
```

Проверка ввода. Каждая функция возвращает список сообщений об ошибках; пустой список означает, что данные корректны. Сервер склеивает сообщения через `"; "` в `ErrorResponse(VALIDATION_FAILED, ...)`. Клиент Android будет вызывать те же функции перед отправкой формы.

```kotlin
object Validation {
    fun employee(personnelNumber: String, lastName: String, firstName: String, middleName: String?, position: String): List<String>
    fun penalty(violationDate: LocalDate, orderDate: LocalDate, description: String, orderNumber: String, today: LocalDate): List<String>
    fun removal(penaltyOrderDate: LocalDate, removalOrderDate: LocalDate, orderNumber: String, today: LocalDate): List<String>
    fun user(login: String, password: String): List<String>
    fun period(from: LocalDate, to: LocalDate): List<String>
}
```

Правила, которые проверяет `Validation`:

- **employee:**
  - табельный номер не пустой и не длиннее 20 символов;
  - фамилия и имя не пустые, каждое не длиннее 100;
  - отчество не длиннее 100; пустая строка трактуется как `null`, это делает сервер;
  - должность не пустая и не длиннее 200.
  - Значения обрезаются (`trim`) до проверки.
- **penalty:**
  - описание не пустое;
  - номер приказа не пустой и не длиннее 50;
  - `violationDate <= orderDate`;
  - `orderDate <= today`.
- **removal:**
  - номер приказа не пустой и не длиннее 50;
  - `penaltyOrderDate <= removalOrderDate <= today`.
- **user:**
  - логин 3–50 символов из `[A-Za-z0-9._-]`;
  - пароль не короче 8 символов.
- **period:** `from <= to`.

## 5. Схема БД (`db/Tables.kt`)

Таблицы и столбцы точно как на схеме из отчёта (гл. 08, `docs/diagrams/practices_8-9.drawio`, вкладка `17_db_schema`). Используется `LongIdTable` / `Table`, enum-ы хранятся как `varchar` по имени (`enumerationByName`).

| Таблица | Столбцы | Ограничения |
|---|---|---|
| `departments` | `id`, `name varchar(200)` | `name` unique |
| `employees` | `id`, `personnel_number varchar(20)`, `last_name`, `first_name`, `middle_name?` (`varchar(100)`), `position varchar(200)`, `department_id → departments`, `is_archived bool default false` | `personnel_number` unique |
| `users` | `id`, `login varchar(50)`, `password_hash varchar(255)`, `role varchar(20)`, `is_blocked bool default false`, `employee_id → employees` | `login` unique, `employee_id` unique |
| `penalties` | `id`, `employee_id → employees`, `type varchar(20)`, `violation_date date`, `description text`, `order_number varchar(50)`, `order_date date`, `status varchar(20)` | check `order_date >= violation_date` |
| `penalty_removals` | `penalty_id` PK → penalties, `order_number varchar(50)`, `order_date date` | |
| `attachments` | `id`, `penalty_id → penalties`, `file_name varchar(255)`, `content_type varchar(50)`, `size_bytes integer`, `storage_key varchar(255)`, `uploaded_at timestamp` | check `size_bytes > 0` |
| `status_events` | `id`, `penalty_id → penalties`, `status varchar(20)`, `changed_at timestamp` | |
| `reports` | `id`, `period_from date`, `period_to date`, `format varchar(10)`, `storage_key varchar(255)`, `created_at timestamp` | check `period_from <= period_to` |
| `report_departments` | `report_id → reports`, `department_id → departments` | PK (`report_id`, `department_id`) |

Даты хранятся как `date(...)`, моменты времени — как `timestamp(...)` из `exposed-kotlin-datetime`. Моменты времени — это `kotlin.time.Instant`. Если колонка Exposed отдаёт `kotlinx.datetime.Instant`, преобразование делается на границе репозитория.

Функция `suspend fun createSchema(db)` вызывает `SchemaUtils.create(всех таблиц)` внутри `suspendTransaction` и запускается при старте до bootstrap.

## 6. Доступ к данным (`db/`)

Репозитории возвращают DTO из `shared:contract`, если те совпадают с нужными данными. Там, где нужны внутренние поля (хеш пароля, `storage_key`), репозиторий возвращает внутренние классы.

```kotlin
data class UserCredentials(val id: UserId, val passwordHash: String, val isBlocked: Boolean, val employeeArchived: Boolean)
data class AuthenticatedUser(val id: UserId, val role: UserRole, val departmentId: DepartmentId, val isBlocked: Boolean)
data class AttachmentRecord(val dto: AttachmentDto, val penaltyId: PenaltyId, val storageKey: String)
data class ReportRecord(val dto: ReportDto, val storageKey: String)

interface IUserRepository {
    suspend fun findCredentials(login: String): UserCredentials?
    suspend fun findAuthenticated(id: UserId): AuthenticatedUser?
    suspend fun findById(id: UserId): UserDto?
    suspend fun findAll(): List<UserDto>                       // sorted by login
    suspend fun existsByLogin(login: String): Boolean
    suspend fun existsByEmployee(employeeId: EmployeeId): Boolean
    suspend fun existsByRole(role: UserRole): Boolean
    suspend fun create(login: String, passwordHash: String, role: UserRole, employeeId: EmployeeId): UserId
    suspend fun setBlocked(id: UserId, blocked: Boolean)
}

interface IEmployeeRepository {
    suspend fun findAllDepartments(): List<DepartmentDto>      // sorted by name
    suspend fun findDepartment(id: DepartmentId): DepartmentDto?
    suspend fun findDepartmentByName(name: String): DepartmentDto?
    suspend fun createDepartment(name: String): DepartmentId
    suspend fun find(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto> // sorted by last, first, middle name
    suspend fun findById(id: EmployeeId): EmployeeDto?
    suspend fun findByPersonnelNumber(number: String): EmployeeDto?
    suspend fun create(request: EmployeeRequest): EmployeeId
    suspend fun update(id: EmployeeId, request: EmployeeRequest)
    suspend fun setArchived(id: EmployeeId)
}

interface IPenaltyRepository {
    suspend fun find(filter: PenaltyFilter): List<PenaltyDto>
    suspend fun findById(id: PenaltyId): PenaltyDto?
    suspend fun findByEmployee(employeeId: EmployeeId): List<PenaltyDto>   // by violation_date, then id, ascending
    suspend fun findActive(): List<Pair<PenaltyId, LocalDate>>             // id + order_date of ACTIVE penalties
    suspend fun create(request: CreatePenaltyRequest): PenaltyId           // status = ACTIVE
    suspend fun setStatus(id: PenaltyId, status: PenaltyStatus)
    suspend fun saveRemoval(id: PenaltyId, removal: PenaltyRemovalDto)
    suspend fun findAttachments(penaltyId: PenaltyId): List<AttachmentDto> // by uploaded_at
    suspend fun findAttachment(id: AttachmentId): AttachmentRecord?
    suspend fun addAttachment(penaltyId: PenaltyId, fileName: String, contentType: String, sizeBytes: Long, storageKey: String, uploadedAt: Instant): AttachmentId
    suspend fun deleteAttachment(id: AttachmentId)
    suspend fun countByDepartment(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): List<SummaryRowDto>
}

interface IEventLogRepository {
    suspend fun add(penaltyId: PenaltyId, status: PenaltyStatus, at: Instant)
    suspend fun findByPenalty(penaltyId: PenaltyId): List<StatusEventDto>  // by changed_at, then id
}

interface IReportRepository {
    suspend fun create(from: LocalDate, to: LocalDate, format: ReportFormat, storageKey: String, departmentIds: List<DepartmentId>, createdAt: Instant): ReportId
    suspend fun findAll(): List<ReportDto>                                  // newest first
    suspend fun findById(id: ReportId): ReportRecord?
}
```

Требования к реализации:

- **`PenaltyDto.expiryDate`** заполняется через `PenaltyRules.expiryDate(orderDate)`, без арифметики в SQL.
- **`PenaltyFilter` в `find`:**
  - `employeeName` — регистронезависимое вхождение в «фамилия имя отчество»;
  - `departmentId` — подразделение сотрудника;
  - `orderDateFrom` и `orderDateTo` включительны;
  - сортировка по `sort`/`direction`; `EMPLOYEE_NAME` сортирует по фамилии, имени и отчеству; при равенстве дополнительно сортировать по `id`.
- **`find` сотрудников:**
  - `query` — регистронезависимое вхождение в ФИО или табельный номер;
  - `includeArchived = false` исключает архивных.
- **`countByDepartment`:**
  - считаются взыскания с `violation_date` в периоде включительно, сгруппированные по подразделению сотрудника;
  - в результат попадают **все** запрошенные подразделения, в том числе с нулями;
  - строки отсортированы по названию подразделения.
- **`findActive`:** решение «пора погашать» принимает сервис через `PenaltyRules.isDue`, а не SQL. Так учитывается 29 февраля.

## 7. Сервисы

`CurrentUser(id: UserId, role: UserRole, departmentId: DepartmentId)` — principal аутентификации.

`class ApiException(val code: ErrorCode, message: String) : RuntimeException(message)`. Сервисы сообщают об ошибках только им.

### «Управление доступом» — `IAuthService`

```kotlin
interface IAuthService {
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun authenticate(id: UserId): CurrentUser?
    suspend fun listUsers(): List<UserDto>
    suspend fun createUser(request: CreateUserRequest): UserDto
    suspend fun setBlocked(actor: CurrentUser, id: UserId, blocked: Boolean): UserDto
}
```

- **`login`:**
  - нет пользователя или неверный пароль → `INVALID_CREDENTIALS` с одинаковым текстом в обоих случаях;
  - `isBlocked` или сотрудник в архиве → `USER_BLOCKED`.
- **`authenticate`** (вызывается из JWT `validate` на каждый запрос): возвращает `null`, если пользователя нет, он заблокирован или его сотрудник в архиве; тогда ответ `401 UNAUTHORIZED`.
- **`createUser`:**
  - `Validation.user`;
  - логин занят → `DUPLICATE_LOGIN`;
  - сотрудник не найден, в архиве или уже имеет учётную запись → `VALIDATION_FAILED`.
- **`setBlocked`:**
  - пользователь не найден → `NOT_FOUND`;
  - блокировать самого себя нельзя → `VALIDATION_FAILED`.
- **`PasswordHasher`:**
  - формат хранения `pbkdf2_sha256$<iterations>$<saltB64>$<hashB64>`;
  - при проверке число итераций берётся из сохранённой строки;
  - сравнение через `MessageDigest.isEqual`;
  - число итераций при создании хеша берётся из `security.passwordHashIterations`, тесты ставят 1000.
- **`JwtService`:** `issue(id): String` и настройка verifier для плагина `Authentication`, issuer и `sub`.

### «Учёт сотрудников и нарушений» — `IRecordService`

```kotlin
interface IRecordService {
    suspend fun departments(): List<DepartmentDto>
    suspend fun findEmployees(actor: CurrentUser, query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto>
    suspend fun getEmployee(actor: CurrentUser, id: EmployeeId): EmployeeDto
    suspend fun createEmployee(request: EmployeeRequest): EmployeeDto
    suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto
    suspend fun archiveEmployee(id: EmployeeId): EmployeeDto
    suspend fun findPenalties(actor: CurrentUser, filter: PenaltyFilter): List<PenaltyDto>
    suspend fun getPenalty(actor: CurrentUser, id: PenaltyId): PenaltyDetailsDto
    suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto
    suspend fun addAttachment(penaltyId: PenaltyId, fileName: String, contentType: String, bytes: ByteArray): AttachmentDto
    suspend fun getAttachment(actor: CurrentUser, id: AttachmentId): StoredFile
    suspend fun deleteAttachment(id: AttachmentId)
}
data class StoredFile(val fileName: String, val contentType: String, val bytes: ByteArray)
```

- **Ограничение для MANAGER** (задаётся здесь, `actor.role == MANAGER`):
  - в списках `departmentId` принудительно ставится своё подразделение; если явно передано чужое → `FORBIDDEN`;
  - при доступе по id сотрудник, взыскание или вложение из чужого подразделения → `FORBIDDEN`.
- **Сотрудники:**
  - `Validation.employee`;
  - подразделение не существует → `VALIDATION_FAILED`;
  - табельный номер занят другим сотрудником → `DUPLICATE_PERSONNEL_NUMBER`;
  - не найден → `NOT_FOUND`;
  - повторное архивирование ничего не меняет и возвращает сотрудника.
- **`createPenalty`:**
  - сотрудник не найден или в архиве → `VALIDATION_FAILED`;
  - `Validation.penalty` с `today` из `Clock` и `TimeZone`;
  - в одной транзакции: `create` и `eventLog.add(ACTIVE, now)`.
- **`getPenalty`:** взыскание, вложения и события.
- **Вложения:**
  - `addAttachment`: взыскание не найдено → `NOT_FOUND`; иначе `IFileStorage.save` (проверка формата и размера), затем `addAttachment` в БД. Если запись в БД не удалась, сохранённый файл удаляется;
  - `deleteAttachment` удаляет и строку, и файл.

### «Контроль сроков» — `IExpiryService` и планировщик

```kotlin
interface IExpiryService {
    suspend fun expireDue(today: LocalDate): Int      // returns how many penalties were expired
    suspend fun removeEarly(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto
}
```

- **`expireDue`:**
  - для каждого активного взыскания с `PenaltyRules.isDue(orderDate, today)`: `setStatus(EXPIRED)` и `eventLog.add(EXPIRED, now)` в одной транзакции на взыскание;
  - повторный запуск в тот же день ничего не меняет.
- **`removeEarly`:**
  - не найдено → `NOT_FOUND`;
  - статус не `ACTIVE` → `PENALTY_NOT_ACTIVE`;
  - `Validation.removal`;
  - в одной транзакции: `saveRemoval`, `setStatus(REMOVED)` и `eventLog.add(REMOVED, now)`.
- **`ExpiryScheduler`:**
  - стартует на `ApplicationStarted`, если `expiry.schedulerEnabled`;
  - сразу вызывает `expireDue(today)`, затем ждёт до следующего `expiry.runAt` в `app.timeZone`, в цикле;
  - отменяется на `ApplicationStopping`;
  - ошибки одного прогона логируются и не останавливают цикл.

### «Отчётность» — `IReportService`

```kotlin
interface IReportService {
    suspend fun employeeCard(actor: CurrentUser, id: EmployeeId): EmployeeCardDto
    suspend fun exportEmployeeCard(actor: CurrentUser, id: EmployeeId, format: ReportFormat): StoredFile
    suspend fun exportPenalties(actor: CurrentUser, filter: PenaltyFilter, format: ReportFormat): StoredFile
    suspend fun summary(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto
    suspend fun createReport(request: CreateReportRequest): ReportDto
    suspend fun listReports(): List<ReportDto>
    suspend fun reportFile(id: ReportId): StoredFile
}
```

- **Права:** ограничение MANAGER такое же, как в `IRecordService`; переиспользовать его, а не дублировать. Список взысканий для экспорта берётся из `IRecordService.findPenalties`.
- **`summary` и `createReport`:**
  - `Validation.period`;
  - пустой `departmentIds` означает все подразделения;
  - несуществующее подразделение → `VALIDATION_FAILED`.
- **`createReport`:**
  - считает сводку;
  - формирует файл в `format`;
  - `IFileStorage.save`;
  - `reportRepository.create` (`report_departments` — фактический список подразделений).
- **Имена файлов** (только ASCII):
  - `penalties_<today>.<ext>`;
  - `card_<personnelNumber>_<today>.<ext>`;
  - `summary_<from>_<to>.<ext>`.
- **Столбцы:**
  - реестр и карточка: «ФИО», «Табельный номер», «Подразделение», «Вид», «Дата нарушения», «Описание», «Номер приказа», «Дата приказа», «Дата погашения», «Статус». Вид и статус берутся из `displayName`, даты в формате `ДД.ММ.ГГГГ`;
  - сводный отчёт: «Подразделение», «Замечаний», «Выговоров», «Всего», последняя строка — «Итого».
- **Форматы:**
  - `CsvWriter(rows: List<List<String>>): ByteArray` — BOM, `;`, CRLF; поле в кавычках, если содержит `;`, `"`, `\r` или `\n`; `"` удваивается;
  - `XlsxWriter(sheetName, rows): ByteArray` через fastexcel, первая строка жирная.
- **Content-Type:**
  - XLSX: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`;
  - CSV: `text/csv; charset=utf-8`.

### «Хранилище файлов» — `IFileStorage`

```kotlin
interface IFileStorage {
    suspend fun save(contentType: String, bytes: ByteArray, validateAttachment: Boolean): String // returns storage key
    suspend fun read(key: String): ByteArray
    suspend fun delete(key: String)
}
```

- Реализация `LocalFileStorage` работает с каталогом `files.dir`. Ключ — случайный UUID, файл лежит в `<dir>/<key>`. Каталог создаётся при старте.
- При `validateAttachment = true`:
  - пустой файл или файл больше `files.maxAttachmentBytes` → `FILE_TOO_LARGE`;
  - тип не из `AttachmentRules.allowedContentTypes` или байты не совпадают с сигнатурой → `UNSUPPORTED_FILE_TYPE`.
- Файлы отчётов сохраняются с `validateAttachment = false`.
- Ввод-вывод выполняется в `Dispatchers.IO`.

## 8. API («Веб-API», `api/`)

Все пути берутся из `ApiRoutes`, имена параметров — из `ApiParams`. Path-параметр `{id}` разбирается в нужный `*Id`; не число → `VALIDATION_FAILED`.

| Метод и путь | Роли | Тело → ответ | Ошибки |
|---|---|---|---|
| `GET /health` | без токена | `OK` | — |
| `POST LOGIN` | без токена | `LoginRequest` → `LoginResponse` | INVALID_CREDENTIALS, USER_BLOCKED |
| `GET USERS` | ADMIN | → `List<UserDto>` | |
| `POST USERS` | ADMIN | `CreateUserRequest` → `201 UserDto` | VALIDATION_FAILED, DUPLICATE_LOGIN |
| `POST USER_BLOCK` / `USER_UNBLOCK` | ADMIN | → `UserDto` | NOT_FOUND, VALIDATION_FAILED |
| `GET DEPARTMENTS` | все | → `List<DepartmentDto>` | |
| `GET EMPLOYEES` | все | query: `QUERY`, `DEPARTMENT_ID`, `INCLUDE_ARCHIVED` → `List<EmployeeDto>` | FORBIDDEN |
| `POST EMPLOYEES` | ADMIN, HR | `EmployeeRequest` → `201 EmployeeDto` | VALIDATION_FAILED, DUPLICATE_PERSONNEL_NUMBER |
| `GET EMPLOYEE` | все | → `EmployeeDto` | NOT_FOUND, FORBIDDEN |
| `PUT EMPLOYEE` | HR | `EmployeeRequest` → `EmployeeDto` | NOT_FOUND, VALIDATION_FAILED, DUPLICATE_PERSONNEL_NUMBER |
| `POST EMPLOYEE_ARCHIVE` | HR | → `EmployeeDto` | NOT_FOUND |
| `GET EMPLOYEE_CARD` | HR, MANAGER | → `EmployeeCardDto` | NOT_FOUND, FORBIDDEN |
| `GET EMPLOYEE_CARD_EXPORT` | HR, MANAGER | query `FORMAT` → файл | NOT_FOUND, FORBIDDEN, VALIDATION_FAILED |
| `GET PENALTIES` | HR, MANAGER | query `PenaltyFilter` → `List<PenaltyDto>` | FORBIDDEN, VALIDATION_FAILED |
| `POST PENALTIES` | HR | `CreatePenaltyRequest` → `201 PenaltyDto` | VALIDATION_FAILED |
| `GET PENALTIES_EXPORT` | HR, MANAGER | query `PenaltyFilter` + `FORMAT` → файл | FORBIDDEN, VALIDATION_FAILED |
| `GET PENALTY` | HR, MANAGER | → `PenaltyDetailsDto` | NOT_FOUND, FORBIDDEN |
| `POST PENALTY_REMOVAL` | HR | `PenaltyRemovalDto` → `PenaltyDto` | NOT_FOUND, PENALTY_NOT_ACTIVE, VALIDATION_FAILED |
| `POST PENALTY_ATTACHMENTS` | HR | multipart, часть `FILE` → `201 AttachmentDto` | NOT_FOUND, FILE_TOO_LARGE, UNSUPPORTED_FILE_TYPE, VALIDATION_FAILED |
| `GET ATTACHMENT` | HR, MANAGER | → файл | NOT_FOUND, FORBIDDEN |
| `DELETE ATTACHMENT` | HR | → `204` | NOT_FOUND |
| `GET REPORTS_SUMMARY` | HR | query `PERIOD_FROM`, `PERIOD_TO`, `DEPARTMENT_ID`* → `SummaryReportDto` | VALIDATION_FAILED |
| `GET REPORTS` | HR | → `List<ReportDto>` | |
| `POST REPORTS` | HR | `CreateReportRequest` → `201 ReportDto` | VALIDATION_FAILED |
| `GET REPORT_FILE` | HR | → файл | NOT_FOUND |

Общее для всех маршрутов:

- Все `/api/*`, кроме `LOGIN`, находятся внутри `authenticate("jwt") { }`. Нет токена или он неверный → `401` и `ErrorResponse(UNAUTHORIZED)`, через `challenge` плагина JWT.
- Роль проверяет `suspend fun ApplicationCall.requireRole(vararg roles: UserRole): CurrentUser` в начале обработчика; при несовпадении → `FORBIDDEN`.
- `StatusPages`:
  - `ApiException` → `code.httpStatus` + `ErrorResponse`;
  - `BadRequestException` / `ContentTransformationException` (битый JSON, неизвестный enum) → `VALIDATION_FAILED`;
  - любое другое исключение → лог + `500 INTERNAL_ERROR` с общим текстом, без стектрейса в ответе.
- Файлы отдаются через `respondBytes` с `Content-Type` и `Content-Disposition: attachment` с именем файла. У вложений имя может быть кириллическим: заголовок должен быть корректным (параметр `filename*` в UTF-8). Это проверяет тест.
- `PenaltyFilter` разбирается из query одной функцией `Parameters.toPenaltyFilter()`; неверный enum или дата → `VALIDATION_FAILED`.
- `CallLogging` установлен. Плагин `RequestValidation` не используется — удалить зависимость `ktor-server-request-validation` из `server/module.yaml` и `libs.versions.toml`.
- Swagger (`/docs`) остаётся без авторизации. Новые маршруты должны появиться в `/docs/documentation.yaml`.

Разрешённая правка контракта: в KDoc `ApiParams.DEPARTMENT_ID` дописать, что отсутствие параметра в `REPORTS_SUMMARY` означает все подразделения.

## 9. Старт приложения

`Application.module()` (Ktor 3 поддерживает `suspend fun Application.module()`; если нет — `runBlocking` только здесь):

1. Создать граф: `createGraphFactory<AppGraph.Factory>().create(environment.config, Clock.System)`.
2. `createSchema(graph.database)`.
3. `graph.bootstrap.run()`:
   - создать подразделения из `bootstrap.departments`, которых нет;
   - если нет ни одного пользователя `ADMIN`: найти или создать сотрудника с табельным номером `ADMIN` («Администратор СУДВ», должность «Администратор СУДВ», подразделение `bootstrap.admin.department`);
   - создать учётную запись ADMIN с логином и паролем из конфига.
   - Повторный запуск ничего не дублирует.
4. Установить плагины: ContentNegotiation, CallLogging, StatusPages, Authentication.
5. Маршруты, Swagger, `ExpiryScheduler`.

## 10. Тестовый харнес (`server/test/TestApp.kt`)

- Тестовые зависимости в `server/module.yaml`:
  - `org.testcontainers:testcontainers-postgresql:2.0.5`;
  - `org.postgresql:postgresql:42.7.14` — нужен только Testcontainers для проверки готовности;
  - `ktor-client-content-negotiation` уже есть.
  - Версии записываются в `libs.versions.toml`.
- Один контейнер `PostgreSQLContainer("postgres:16-alpine")` на весь прогон, запускается лениво в `object`.
- `fun apiTest(block: suspend ApplicationTestBuilder.() -> Unit)`:
  1. Очищает БД, удаляя все таблицы, если они есть. Тогда схему и bootstrap создаёт сам `module()`.
  2. Запускает `testApplication` с `environment { config = ApplicationConfig("application.yaml").mergeWith(MapApplicationConfig(...)) }`. Переопределяются: `database.*` контейнера; `files.dir` — временный каталог; `security.passwordHashIterations = "1000"`; `expiry.schedulerEnabled = "false"`.
  3. Вызывает `application { module() }`.
  4. Даёт JSON-клиент `api` с `ContentNegotiation { json() }`.
- Помощники:
  - `login(login, password): String` — возвращает токен;
  - `adminToken()`;
  - `createEmployee(...)` и `createUser(role, departmentName)` возвращают токен HR или MANAGER, созданного через API;
  - `HttpRequestBuilder.bearer(token)`.
- Перевести на харнес существующие `RoutingTest` и `SwaggerTest`: после фазы 2 `module()` требует БД.
- Тесты с Docker должны проходить командой `./kotlin test -m server`.

## 11. Фазы

Каждая фаза заканчивается зелёными `./kotlin build` и `./kotlin test`. Тесты, перечисленные в фазе, обязательны; дополнительные приветствуются.

### Фаза 1. Правила домена

- **Объём:** раздел 4 целиком (`shared:domain`).
- **Тесты (`shared/domain/test/`):**
  - `expiryDate`: обычная дата; 29.02.2028 → 28.02.2029;
  - `isDue`: накануне — `false`, в день погашения — `true`, позже — `true`;
  - `remainingDays`: 0 после даты погашения;
  - `matchesSignature`: PDF, JPEG, подмена (байты PNG с типом `image/jpeg`);
  - для каждой функции `Validation` — один корректный случай и все невалидные ветки;
  - `displayName` для всех значений enum-ов.
- **Коммит:** `feat(domain): add penalty rules, validation and display names`.

### Фаза 2. Инфраструктура сервера

- **Объём:**
  - раздел 3 (конфиг + `AppConfig`) и раздел 5 (таблицы + `createSchema`);
  - `Transactor`, `ApiException` + `StatusPages`, `CallLogging`;
  - граф с `Clock`/`TimeZone`/`AppConfig`, `suspend module()` с созданием схемы (bootstrap пока заглушкой не делать — его делает фаза 3);
  - тестовый харнес (раздел 10), перевод существующих тестов;
  - удаление `request-validation`.
- **Тесты:**
  - после старта в БД есть все 9 таблиц (запрос к `information_schema.tables`);
  - check-ограничение `order_date >= violation_date` срабатывает на прямой вставке;
  - тест транзакции из раздела 2: вложенный `tx` откатывается вместе с внешним;
  - `StatusPages`: неизвестное исключение из тестового маршрута → `500 INTERNAL_ERROR`, в теле нет стектрейса. Тестовый маршрут объявляется только в тесте;
  - `/health` и `/docs/documentation.yaml` работают.
- **Коммит:** `feat(server): add db schema, config, error handling and test harness`.

### Фаза 3. Управление доступом

- **Объём:**
  - `PasswordHasher`, `JwtService`, `IUserRepository`, `IEmployeeRepository` целиком, `IAuthService`, `Bootstrap`;
  - плагин `Authentication` (JWT + `challenge`), `requireRole`;
  - маршруты `LOGIN`, `USERS*`, `DEPARTMENTS`.
- **Тесты:**
  - `PasswordHasher`: верный и неверный пароль; одинаковые пароли дают разные хеши;
  - bootstrap: все подразделения из конфига на месте; admin входит с паролем из конфига; повторный старт на той же БД без очистки ничего не дублирует;
  - login: верный пароль → токен + `UserDto`; неверный пароль и несуществующий логин → `401 INVALID_CREDENTIALS` с одинаковым `message`; заблокированный → `403 USER_BLOCKED`;
  - запрос без токена, с мусорным и с просроченным токеном → `401 UNAUTHORIZED`;
  - создание пользователя: успех; `DUPLICATE_LOGIN`; сотрудник уже с учёткой → `VALIDATION_FAILED`; слабый пароль → `VALIDATION_FAILED`;
  - после блокировки старый токен пользователя → `401`, после разблокировки снова работает; блокировка себя → `VALIDATION_FAILED`;
  - HR и MANAGER на `GET USERS` → `403 FORBIDDEN`.
- **Коммит:** `feat(server): add authentication, users and bootstrap`.

### Фаза 4. Учёт сотрудников и нарушений

- **Объём:**
  - `IRecordService` без вложений, `IPenaltyRepository` без вложений и `countByDepartment`, `IEventLogRepository`;
  - маршруты сотрудников и взысканий (`EMPLOYEES*`, `PENALTIES`, `PENALTY`);
  - `toPenaltyFilter()`.
- **Тесты:**
  - CRUD сотрудника, архив скрывает из списка по умолчанию и виден с `includeArchived=true`, поиск по части фамилии и по табельному номеру;
  - `DUPLICATE_PERSONNEL_NUMBER` при создании и при изменении;
  - создание взыскания: `expiryDate` = приказ + 1 год, статус `ACTIVE`, в деталях одно событие `ACTIVE`; архивный сотрудник → `VALIDATION_FAILED`; `orderDate` в будущем → `VALIDATION_FAILED`;
  - фильтры реестра: каждый отдельно и все вместе; сортировка `EMPLOYEE_NAME ASC` и `ORDER_DATE DESC`;
  - MANAGER: видит только своё подразделение; чужое `departmentId` → `403`; карточка взыскания и сотрудник чужого подразделения → `403`; `POST PENALTIES` → `403`;
  - ADMIN: `GET EMPLOYEES` → `200`, `GET PENALTIES` → `403`.
- **Коммит:** `feat(server): add employees and penalties registry`.

### Фаза 5. Контроль сроков

- **Объём:** `IExpiryService`, маршрут `PENALTY_REMOVAL`, `ExpiryScheduler`.
- **Тесты:**
  - `expireDue`:
    - накануне даты погашения взыскание остаётся `ACTIVE`;
    - в день погашения становится `EXPIRED`, в журнале появляется событие `EXPIRED`;
    - повторный вызов возвращает 0;
    - `REMOVED` не трогается;
    - взыскания достаются через `graph` или публичный метод теста;
  - досрочное снятие: `REMOVED`, `removal` заполнен, событие в журнале; повторное снятие → `PENALTY_NOT_ACTIVE`; дата снятия раньше приказа → `VALIDATION_FAILED`; MANAGER → `403`;
  - планировщик: при `schedulerEnabled = true` и уже просроченном взыскании оно становится `EXPIRED` вскоре после старта (ждать с таймаутом, без `Thread.sleep` дольше 5 с).
- **Коммит:** `feat(server): add penalty expiry and early removal`.

### Фаза 6. Вложения и хранилище файлов

- **Объём:**
  - `IFileStorage`/`LocalFileStorage`;
  - вложения в `IPenaltyRepository` и `IRecordService`;
  - маршруты `PENALTY_ATTACHMENTS`, `ATTACHMENT`.
  - Multipart принимается с лимитом чуть больше `maxAttachmentBytes`; превышение лимита Ktor → `FILE_TOO_LARGE`.
- **Тесты:**
  - загрузка PDF и JPEG → `201`, вложение есть в деталях взыскания;
  - скачивание возвращает те же байты и корректный `Content-Disposition` для имени `Акт №1.pdf`;
  - PNG → `415`; PNG с объявленным `image/jpeg` → `415`; размер больше лимита → `413` (в тесте лимит уменьшен через конфиг);
  - удаление → `204`, файл исчез с диска, повторное скачивание → `404`;
  - MANAGER скачивает вложение своего подразделения → `200`, чужого → `403`, загрузка → `403`.
- **Коммит:** `feat(server): add penalty attachments and file storage`.

### Фаза 7. Отчётность

- **Объём:**
  - `IReportService`, `CsvWriter`, `XlsxWriter`;
  - `IPenaltyRepository.countByDepartment`, `IReportRepository`;
  - маршруты `EMPLOYEE_CARD*`, `PENALTIES_EXPORT`, `REPORTS*`;
  - зависимость `fastexcel`, правка KDoc `ApiParams.DEPARTMENT_ID`.
- **Тесты:**
  - `CsvWriter`: BOM, `;`, CRLF, экранирование кавычек и `;` (unit-тест);
  - карточка: взыскания по возрастанию даты нарушения;
  - экспорт карточки и реестра в CSV: заголовок и строки совпадают с данными; в XLSX — открыть результат как ZIP и убедиться, что это корректный XLSX (есть `xl/workbook.xml`), `Content-Type` верный;
  - сводка:
    - подсчёт по подразделениям за период;
    - взыскание вне периода не учитывается;
    - подразделение без взысканий даёт нули;
    - без `DEPARTMENT_ID` — все подразделения;
    - `from > to` → `VALIDATION_FAILED`;
  - архив: `POST REPORTS` → `201`, отчёт в `GET REPORTS` с подразделениями, `GET REPORT_FILE` отдаёт файл без повторной генерации (байты совпадают при повторном скачивании);
  - MANAGER: карточка своего сотрудника → `200`, чужого → `403`; `GET REPORTS_SUMMARY` → `403`.
- **Коммит:** `feat(server): add reports, exports and report archive`.

### Фаза 8. Развёртывание и документация

- **Объём:**
  - `deployment/docker-compose.local.yml`: в `server` добавить переменные `JWT_SECRET`, `ADMIN_LOGIN`, `ADMIN_PASSWORD`, `FILES_DIR=/srv/sudv/files` и том `files-data:/srv/sudv/files`;
  - `deployment/.env.example`: те же переменные с примерами;
  - `Dockerfile`: каталог `/srv/sudv/files` принадлежит пользователю `sudv`;
  - `AGENTS.md`: обновить «Текущее состояние», «Стек», конфигурацию, права и тесты (нужен Docker).
- **Проверка (выполняет исполнитель):** `make dev-compose`; `POST /api/auth/login` admin-ом; создание HR и сотрудника; регистрация взыскания; загрузка вложения; `make dev-stop` → `make dev-compose` — вложение и данные на месте; `make dev-destroy`.
- **Коммит:** `feat(deployment): configure auth and file storage for compose`.

## 12. Протокол судьи

После каждой фазы судья:

1. Читает отчёт исполнителя и **весь diff**.
2. Сам запускает `./kotlin build` и `./kotlin test`. Если отчёт исполнителя и реальный результат расходятся, фаза сразу возвращается на доработку.
3. Проходит чек-лист:
   - всё из «Объёма» фазы сделано, лишнего из других фаз нет;
   - все обязательные тесты есть и **проверяют результат** (статус, тело, состояние БД), а не только «не упало»;
   - нет блокирующего `transaction {}`, JDBC в основном коде, DAO, `runBlocking` вне `module()`;
   - id везде value class, а не `Long`;
   - контракт не менялся, кроме разрешённого;
   - ограничение MANAGER нельзя обойти параметром запроса;
   - ошибки отдаются через `ApiException` → `ErrorResponse`, нет `respond(HttpStatusCode.X, "строка")`;
   - секреты не захардкожены вне `application.yaml` с переменными окружения;
   - срок погашения вычисляется только через `PenaltyRules`;
   - комментарии только по делу и на английском, сообщения пользователю — на русском;
   - имена интерфейсов совпадают с отчётом (`IAuthService`, `IRecordService`, …).
4. Выносит вердикт:
   - **принято** — судья коммитит с сообщением фазы;
   - **на доработку** — список конкретных замечаний с файлом и строкой; исполнитель исправляет только их.

## 13. Вне объёма

- Android-клиент.
- Боевой compose-файл и HTTPS.
- Смена роли и пароля пользователя, редактирование и удаление взысканий, управление подразделениями через API — этого нет в таблице функций отчёта.
- Постраничный вывод реестра.
- PDF-экспорт.
