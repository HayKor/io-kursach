# AGENTS.md

Курсовая работа по дисциплине «Информатизация организации».
В репозитории два независимых продукта:

1. **Отчёт** (`docs/`) — техническое задание и модели на Typst. Его сдают и **защищают устно**.
2. **Проект СУДВ** (`android-app/`, `server/`, `shared/`) — программная реализация системы, описанной в отчёте.

## Тема

**СУДВ — Система учёта дисциплинарных взысканий и замечаний.**

- Заказчик (учебный): ООО «Апельсин». Разработчик: Багинян Артур Варданович.
- Сроки работ: 22.09.2026 – 22.12.2026.
- Пользователи: сотрудники отдела кадров и руководители подразделений.
- Цели: снизить юридические риски при оформлении взысканий, сократить трудозатраты кадрового делопроизводства, сделать трудовую дисциплину прозрачнее.

### Предметная область (зафиксирована в отчёте, гл. 02 и 08)

- **Подсистемы:** учёт сотрудников и нарушений; контроль статусов и сроков давности; формирование отчётности.
- **Роли:** `ADMIN` (администратор), `HR` (кадровик), `MANAGER` (руководитель — видит только своё подразделение).
- **Взыскание:** тип `REMARK` (замечание) или `REPRIMAND` (выговор); статус `ACTIVE` («Действует»), `EXPIRED` («Погашено»), `REMOVED` («Снято досрочно»).
- **Дата погашения = дата приказа + 1 год.** Она не хранится в БД, а вычисляется (правило в `shared:domain`). Ежедневный планировщик переводит истёкшие взыскания в `EXPIRED` и пишет событие в журнал.
- Досрочное снятие хранит реквизиты приказа (`penalty_removals`).
- Сотрудники не удаляются, а архивируются (`is_archived`), чтобы сохранить историю.
- Вложения: только PDF/JPEG, с лимитом размера. Сами файлы лежат в `/srv/sudv/files`, в БД — только метаданные и `storage_key`.
- Отчёты: персональная карточка (не архивируется) и сводный отчёт по подразделениям за период (архивируется), экспорт в XLSX/CSV.
- Таблицы БД `sudv`: `departments`, `employees`, `users`, `penalties`, `penalty_removals`, `attachments`, `status_events`, `reports`, `report_departments`. Схема приведена к 3НФ.

## Структура репозитория

```
android-app/        мобильное приложение (Android, Compose)
server/             серверное приложение (Ktor, JVM)
shared/contract/    DTO, адреса API, коды ошибок — общий контракт клиента и сервера
shared/domain/      правила предметной области: статусы, роли, сроки, валидация
deployment/         Docker: образ сервера, docker-compose для локального запуска, пример .env
libs.versions.toml  версии всех внешних библиотек
common.module-template.yaml  общие настройки модулей (Kotlin, JDK)
project.yaml        список модулей
kotlin, kotlin.bat  обёртка Kotlin Toolchain (бывший Amper), версия 0.13
docs/               отчёт (Typst) — см. ниже
refs/               методички и задания к практическим занятиям (PDF)
```

## Проект (код)

Исходники лежат прямо в `src/` и `test/` модуля, без вложенных папок по пакету (Kotlin не требует совпадения папок с пакетом). Новые файлы класть так же. Пакеты:
- `shared:domain` — `ru.haykor.sudv.domain`;
- `shared:contract` — `ru.haykor.sudv.contract`;
- `server` и `android-app` — `ru.haykor.sudv`.

### Сборка и запуск

Система сборки — **Kotlin Toolchain** (не Gradle). Все команды через обёртку из корня:

```bash
./kotlin build                          # собрать всё
./kotlin build -m server                # один модуль
./kotlin test -m server                 # тесты сервера
./kotlin run -m server                  # сервер на :8080
./kotlin run -m android-app -d emulator-5554   # установить и запустить на эмуляторе
./kotlin show dependencies -m <module> --scope compile|runtime
./kotlin show settings -m <module>      # итоговые настройки модуля
```

Исполняемый jar сервера: `./kotlin package -m server -f executable-jar` → `build/tasks/_server_executableJarJvm/server-jvm-executable.jar`.

Для работы с БД серверу нужен PostgreSQL. Настройки подключения в `server/resources/application.yaml`, их можно переопределить переменными окружения `DB_URL`, `DB_USER`, `DB_PASSWORD`. По умолчанию: `r2dbc:postgresql://localhost:5432/sudv`, пользователь и пароль `sudv` (без схемы `r2dbc:pool:` — пул собирается явно в `AppGraph`, чтобы его можно было закрыть при остановке приложения, см. «Подводные камни»). Схема БД создаётся автоматически при старте (`SchemaUtils.create` по таблицам Exposed), миграций нет. Остальные настройки — `JWT_SECRET`, `ADMIN_LOGIN`, `ADMIN_PASSWORD`, `FILES_DIR` — тоже переопределяются переменными окружения; при старте сервер создаёт учётную запись `ADMIN` с логином и паролем из `ADMIN_LOGIN`/`ADMIN_PASSWORD`, если в БД ещё нет ни одной учётной записи с этой ролью. Временная БД для проверки:

```bash
docker run -d --rm --name sudv-pg -e POSTGRES_DB=sudv -e POSTGRES_USER=sudv -e POSTGRES_PASSWORD=sudv -p 5432:5432 postgres:16
```

Тесты сервера поднимают настоящий PostgreSQL через Testcontainers (`postgres:16-alpine`), поэтому для `./kotlin test -m server` нужен запущенный Docker.

### Docker (`deployment/`)

- `Dockerfile` — двухэтапная сборка: на первом этапе jar собирается через `./kotlin package` (кэш toolchain в BuildKit cache mount), на втором запускается в `eclipse-temurin:21-jre` как `/app/sudv-server.jar` от непривилегированного пользователя. Контекст сборки — корень репозитория, лишнее отсекает `Dockerfile.dockerignore`.
- `docker-compose.local.yml` — сервер и PostgreSQL 16 для локальных тестов. Сервер ждёт готовности БД (healthcheck), порт БД открыт только на `127.0.0.1`.
- Переменные окружения берутся из `deployment/.env.dev` (не коммитится); образец — `deployment/.env.example` (коммитится). Сначала скопировать: `cp .env.example .env.dev`.

Запуск — через `Makefile` в корне (при первом запуске он сам создаёт `.env.dev` из примера):

```bash
make dev-compose   # собрать образ и запустить сервер + БД в фоне (http://localhost:8080)
make dev-logs      # логи
make dev-stop      # остановить, данные БД сохраняются
make dev-destroy   # остановить и удалить данные БД
```

Если запускать `docker compose` руками, `--env-file deployment/.env.dev` обязателен: без него compose сразу завершается с ошибкой о незаданных переменных — это касается не только переменных БД, но и `JWT_SECRET`, `ADMIN_LOGIN`, `ADMIN_PASSWORD` сервиса `server`. Вложения сервис пишет в именованный том `files-data`, смонтированный на `/srv/sudv/files`.

### Стек

| Модуль | Библиотеки |
|---|---|
| `android-app` | Compose + Material 3 (Expressive), Decompose (навигация и компоненты), Metro (DI), Ktor client (OkHttp + kotlinx-json), kotlinx-serialization |
| `server` | Ktor server (Netty, YAML-конфиг, content negotiation, status pages, call logging, auth + JWT, Swagger UI + OpenAPI), Exposed **через R2DBC** (`exposed-r2dbc`, `r2dbc-postgresql`, `r2dbc-pool`), `exposed-kotlin-datetime`, `fastexcel` (запись XLSX), Metro, logback; тесты — Testcontainers + JDBC-драйвер PostgreSQL (только в тестах) |
| `shared:contract` | `shared:domain`, kotlinx-serialization-json, kotlinx-datetime (все экспортируются) |
| `shared:domain` | `kotlinx-serialization-core` (аннотация `@Serializable` на id) и kotlinx-datetime (все экспортируются) |

Версии — только в `libs.versions.toml`, в `module.yaml` ссылки вида `$libs.xxx`. Исключение — Compose: используется встроенный каталог `$compose.*`, потому что его версия привязана к настройке `compose` toolchain. У Ktor `applyBom: false`, чтобы версию задавал только каталог.

### OpenAPI / Swagger

- Swagger UI: `http://localhost:8080/docs`, спецификация: `/docs/documentation.yaml` (отдаётся в JSON). Настройка в `server/src/Swagger.kt`.
- Пути, методы и схемы выводит **плагин компилятора Ktor** (`io.ktor.ktor-compiler-plugin`, опции `openApiEnabled`, `openApiCodeInference`) по коду маршрутов: `call.receive<T>()` / `call.respond<T>()`. В toolchain нет встроенной настройки — плагин подключён вручную в `server/module.yaml` через `compilerPlugins`, как Metro.
- Если вывода не хватает, маршрут дополняется `.describe { … }` (экспериментальное API, `@OptIn(ExperimentalKtorApi::class)`).
- `SwaggerTest` проверяет, что маршруты попадают в спецификацию: если плагин перестанет срабатывать, тест упадёт.

### Архитектурные правила

- `shared:contract` и `shared:domain` **не зависят** от Android и Ktor. `contract` зависит от `domain` (и экспортирует его), обратная зависимость запрещена: enum-ы `UserRole`, `PenaltyType`, `PenaltyStatus`, `ReportFormat` живут в `domain`, DTO используют их напрямую.
- Контракт API — `shared:contract`: адреса в `ApiRoutes` (шаблоны `{id}` для сервера, функции для клиента), имена параметров в `ApiParams`, ошибки — `ErrorResponse` + `ErrorCode` (с HTTP-статусом). Даты — `kotlinx.datetime.LocalDate`, моменты времени — `kotlin.time.Instant`, оба в JSON как ISO-строки. Ответы — `*Dto`, тела запросов — `*Request`.
- Идентификаторы сущностей — `@Serializable @JvmInline value class <Entity>Id(val value: Long)` в `shared:domain` (`Ids.kt`), голый `Long` для id не использовать. В JSON id — обычное число. В common-коде `@JvmInline` нужно импортировать явно (`kotlin.jvm.JvmInline`).
- Клиент и сервер общаются только через REST/JSON API, описанное в `shared:contract`.
- Правила предметной области (статусы, переходы, сроки, валидация) пишутся один раз, в `shared:domain`; срок погашения взыскания вычисляется только через `PenaltyRules.expiryDate` — ни в SQL, ни где-либо ещё.
- DI на обеих сторонах — **Metro** (плагин компилятора, граф проверяется при компиляции). Граф — `AppGraph`: на сервере он создаётся в `Application.module()` через `createGraphFactory<AppGraph.Factory>().create(environment.config, Clock.System)`, на Android он хранится в `SudvApplication.graph`.
- Пакеты сервера `ru.haykor.sudv.<компонент>` лежат в одноимённых подпапках `server/src/<компонент>/`: `api` («Веб-API»), `auth` («Управление доступом»), `records` («Учёт сотрудников и нарушений»), `expiry` («Контроль сроков»), `reports` («Отчётность»), `files` («Хранилище файлов»), `db` («Доступ к данным»), `config` (конфигурация и bootstrap).
- Права: роль проверяют маршруты (`requireRole`), ограничение `MANAGER` на своё подразделение — сервисы. Вкратце: `ADMIN` управляет учётными записями; `HR` работает с сотрудниками, взысканиями, вложениями и отчётами; `MANAGER` только читает сотрудников, взыскания и вложения своего подразделения. `MANAGER` не может указать чужое подразделение даже параметром запроса — такой запрос и обращение по id к чужим данным отклоняются `FORBIDDEN`, а не отфильтровываются молча.
- БД: только Exposed **DSL** (не DAO — `exposed-dao-r2dbc` экспериментальный), все запросы внутри `suspendTransaction { }`. Блокирующий `transaction { }` — это JDBC, не использовать.
- Room на Android **не используется**: от него отказались намеренно.
- Навигация на Android — Decompose: `RootComponent` / `DefaultRootComponent`, конфигурации экранов — `@Serializable`.

### Текущее состояние

Сервер реализует компоненты из отчёта (гл. 06–07): «Веб-API» (маршруты в `api/`), «Управление доступом» (`IAuthService`, JWT, хеширование паролей, bootstrap первого `ADMIN`), «Учёт сотрудников и нарушений» (`IRecordService`), «Контроль сроков» (`IExpiryService` и ежедневный планировщик), «Отчётность» (`IReportService`, экспорт в XLSX/CSV, архив отчётов), «Хранилище файлов» (`IFileStorage`) и «Доступ к данным» (репозитории `I*Repository` на Exposed/R2DBC). Схема БД создаётся при старте, миграций нет. Swagger UI — на `/docs`. Локальный запуск через `deployment/docker-compose.local.yml` и `Makefile` сохраняет данные и файлы вложений в именованных томах между перезапусками.

Что предстоит реализовать: Android-клиент — «Клиент API» (`IApiClient`, хранение токена) и экраны; compose-файл для боевого развёртывания (по отчёту: контейнеры JVM 21 и PostgreSQL 16, том `/srv/sudv/files`).

### Подводные камни (уже наступали)

- **JDK 21 для всех модулей** задан в `common.module-template.yaml`. Если собрать `shared/*` под JDK по умолчанию (25), сервер на JVM 21 падает с `UnsupportedClassVersionError`. Шаблон должен подключаться ко **всем** модулям.
- Версию Kotlin задаёт шаблон. Блок `settings` в `project.yaml` toolchain **игнорирует**.
- Compose 1.12.1 требует `compileSdk: 37`, с 36 сборка падает на проверке AAR-метаданных.
- `ui-tooling` подключён как `runtime-only`. Превью Compose в IDE работает только в **IntelliJ IDEA 2026.2.1+** с актуальным плагином Kotlin Toolchain (исправление KTC-5433). В старых версиях превью не видит runtime-зависимости.
- В тестах Ktor модули из конфига загружаются только через `testApplication { configure("application.yaml") }`, иначе маршрутов нет (404).
- `executable-jar` содержит `logback.xml` дважды, поэтому в `logback.xml` стоит `NopStatusListener`, иначе logback при каждом старте печатает свой статус. Есть известный баг toolchain 0.13: `executable-jar` может терять зависимости с одинаковыми именами файлов. Если после обновления зависимостей появится `NoClassDefFoundError` только в jar или в Docker, проверять это в первую очередь.
- `kotlinx-datetime` на сервере разрешается в вариант `0.8.0-0.6.x-compat` (его требует `exposed-kotlin-datetime`). `LocalDate` и его сериализатор в нём есть, это не ошибка.
- Плагины компилятора (Metro) и KSP-процессоры подключаются в `settings.kotlin.compilerPlugins` / `settings.kotlin.ksp`, `$libs`-ссылки там работают. Что плагин реально сработал, проверять по сгенерированным классам (`AppGraph$Impl`), а не по успешной сборке.
- Ktor 3.6.1 кодирует `filename*` в `ContentDisposition` без percent-encoding не-ASCII символов, поэтому заголовок для вложений собирается вручную в `api/ContentDisposition.kt`.
- `receiveMultipart(formFieldLimit)` ограничивает и файловые части; превышение лимита обрывает запрос необрабатываемым `500` вместо ответа с кодом ошибки. Поэтому размер вложения проверяется по `Content-Length` и ограниченным чтением файловой части, а не лимитом Ktor.
- `fastexcel` (через `opczip`) пишет нестандартный data descriptor: `ZipInputStream` принимает его за несовпадение размера, хотя архив корректный. Тесты читают XLSX через `ZipFile` (центральный каталог) вместо `ZipInputStream`.
- В `testApplication` модули из конфига загружаются автоматически при первом HTTP-вызове; если тест обращается к БД до него (например, напрямую к `AppGraph`), нужен явный `startApplication()`.
- Пул соединений R2DBC нужно закрывать на `ApplicationStopped` (`disposeLater()`, не блокирующий `dispose()`), иначе соединения утекают между тестовыми приложениями и быстро исчерпывают лимит Postgres.

## Отчёт (`docs/`)

Typst; схемы в draw.io экспортируются в PDF автоматически.

```bash
cd docs
make            # все документы в docs/build/*.pdf
make full       # итоговый документ (src/index.typ)
make p12 | p34 | p69   # отдельные практики
make watch-p34  # пересборка при изменениях
make release    # копии PDF с именами для сдачи в docs/output/
```

- `src/chapters/` — главы: 01 общие сведения, 02 структура и функции, 03 модели ARIS, 04 eEPC, 05 виды обеспечения, 06 программное, 07 техническое, 08 информационное.
- `src/practices_*.typ` — сборки глав под конкретные практические занятия (1–2, 3–4, 6–9); `src/index.typ` — итоговый документ.
- `diagrams/*.drawio` — каждая вкладка экспортируется в `src/img/<имя вкладки>.pdf` (`diagrams/export.sh`, нужен `drawio`).
- `include/settings.typ`, `include/report.typ` — оформление.
- Собранные `docs/build/*.pdf` **коммитятся** (корневой `/build/` — нет).

### Правила работы с отчётом

- **Каждое слово придётся защищать.** Добавлять только то, что требует методичка или ГОСТ и что автор может объяснить одной фразой. Неочевидные решения обосновывать прямо в тексте. При сомнении писать проще, а не богаче. Опираться на примеры из методичек в `refs/`.
- Технические идентификаторы (модули, интерфейсы, артефакты, пути, расширения) оформлять как inline raw в backticks: `` `shared:domain` ``, `` `IUserRepository` ``. Названия компонентов на русском — в «ёлочках» («Веб-API»). Следить за шириной колонок таблиц: моноширинные имена не переносятся.

### Известные расхождения отчёта и кода

Решено отчёт пока не править, но на защите об этом могут спросить:

- в отчёте модуль называется `android`, в коде — `android-app`;
- в отчёте сервер работает с БД «через JDBC», в коде — R2DBC;
- в отчёте конфиг сервера — `application.conf`, в коде — `application.yaml`;
- в таблице программных средств (гл. 06) нет Decompose и Metro.

## Git

- Сообщения коммитов: `feat: …`, `chore: …` (Conventional Commits, на английском, со строчной буквы).
- Не коммитить `build/`, `.idea/` и `.env*`, кроме `.env.example` (есть в `.gitignore`).
