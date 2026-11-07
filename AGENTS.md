# AGENTS.md

Курсовая работа по дисциплине «Информатизация организации».
В репозитории два независимых продукта:

1. **Отчёт** (`docs/`) — техническое задание и модели на Typst. Его сдают и **защищают устно**.
2. **Проект СУДВ** (`server/`, `shared/`, `client-*/`, `android-app/`, `web-app/`) — программная реализация системы, описанной в отчёте.

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
server/             серверное приложение (Ktor, JVM)
shared/contract/    DTO, адреса API, коды ошибок — общий контракт клиента и сервера
shared/domain/      правила предметной области: статусы, роли, сроки, валидация
client-domain/      интерфейсы, через которые UI обращается к серверу (`IApiClient`)
client-data/        «Клиент API»: реализация `IApiClient` на Ktor client, хранение токена
client-ui/          экраны (Compose Multiplatform) и компоненты Decompose
android-app/        точка входа Android: `Activity`, граф Metro
web-app/            точка входа веб-клиента (Kotlin/Wasm в браузере), граф Metro
deployment/         Docker: образ сервера, docker-compose для локального запуска, пример .env
libs.versions.toml  версии всех внешних библиотек
common.module-template.yaml  общие настройки модулей (Kotlin, JDK)
metro.module-template.yaml   подключение Metro (runtime и плагин компилятора)
project.yaml        список модулей
kotlin, kotlin.bat  обёртка Kotlin Toolchain (бывший Amper), версия 0.13
docs/               отчёт (Typst) — см. ниже
refs/               методички и задания к практическим занятиям (PDF)
```

## Проект (код)

Пакет отражает имя модуля: `ru.haykor.sudv.<модуль>`. Исходники лежат прямо в `src/` и `test/` модуля, без папок `ru/haykor/sudv/…` (Kotlin не требует совпадения папок с пакетом); подпапки внутри `src/` повторяют оставшуюся часть пакета, например `server/src/records/data/` — это `ru.haykor.sudv.server.records.data`. Пакеты:
- `shared:domain` — `ru.haykor.sudv.shared.domain`;
- `shared:contract` — `ru.haykor.sudv.shared.contract`;
- `server` — `ru.haykor.sudv.server`;
- `client-domain`, `client-data`, `client-ui` — `ru.haykor.sudv.client.domain` / `.data` / `.ui`;
- `web-app` — `ru.haykor.sudv.web.app`;
- `android-app` — `ru.haykor.sudv.android.app` (дефис в имени модуля заменён точкой). Это же `namespace` в `module.yaml`; `applicationId` остался `ru.haykor.sudv`, чтобы не менялся идентификатор установленного приложения.

### Сборка и запуск

Система сборки — **Kotlin Toolchain** (не Gradle). Все команды через обёртку из корня:

```bash
./kotlin build                          # собрать всё
./kotlin build -m server                # один модуль
./kotlin test -m server                 # тесты сервера
./kotlin run -m server                  # сервер на :8080
./kotlin run -m android-app -d emulator-5554   # установить и запустить на эмуляторе
./kotlin run -m web-app --port 8081     # веб-клиент для быстрых правок; порт по умолчанию 8080 занят сервером
./kotlin build -m web-app && WEB_DIR=$PWD/build/tasks/_web-app_buildWasmJsAppWasmJsDebug ./kotlin run -m server
                                        # веб-клиент через сервер на :8080 — прямые ссылки и перезагрузка работают
./kotlin test -m client-data            # тесты «Клиента API» (MockEngine, без сервера)
./kotlin test -m client-ui --platform android  # тесты переходов между экранами (без сервера)
./kotlin show dependencies -m <module> --scope compile|runtime
./kotlin show settings -m <module>      # итоговые настройки модуля
```

Исполняемый jar сервера: `./kotlin package -m server -f executable-jar` → `build/tasks/_server_executableJarJvm/server-jvm-executable.jar`.

Для работы с БД серверу нужен PostgreSQL. Настройки подключения в `server/resources/application.yaml`, их можно переопределить переменными окружения `DB_URL`, `DB_USER`, `DB_PASSWORD`. По умолчанию: `r2dbc:postgresql://localhost:5432/sudv`, пользователь и пароль `sudv` (без схемы `r2dbc:pool:` — пул собирается явно в `AppGraph`, чтобы его можно было закрыть при остановке приложения, см. «Подводные камни»). Схема БД создаётся автоматически при старте (`SchemaUtils.create` по таблицам Exposed), миграций нет. Остальные настройки — `JWT_SECRET`, `ADMIN_LOGIN`, `ADMIN_PASSWORD`, `FILES_DIR`, `CORS_ORIGINS`, `WEB_DIR` — тоже переопределяются переменными окружения; при старте сервер создаёт учётную запись `ADMIN` с логином и паролем из `ADMIN_LOGIN`/`ADMIN_PASSWORD`, если в БД ещё нет ни одной учётной записи с этой ролью. Временная БД для проверки:

```bash
docker run -d --rm --name sudv-pg -e POSTGRES_DB=sudv -e POSTGRES_USER=sudv -e POSTGRES_PASSWORD=sudv -p 5432:5432 postgres:16
```

Тесты сервера поднимают настоящий PostgreSQL через Testcontainers (`postgres:16-alpine`), поэтому для `./kotlin test -m server` нужен запущенный Docker.

### Docker (`deployment/`)

- `Dockerfile` — двухэтапная сборка: на первом этапе через `./kotlin package` собирается jar сервера и через `./kotlin build -m web-app -v release` веб-клиент (кэш toolchain в BuildKit cache mount); в `index.html` клиента адрес API обнуляется — его раздаёт тот же сервер. На втором этапе всё запускается в `eclipse-temurin:21-jre`: `/app/sudv-server.jar` от непривилегированного пользователя, веб-клиент в `/app/web` (`WEB_DIR`). После `make dev-compose` клиент открывается на `http://localhost:8080`. Контекст сборки — корень репозитория, лишнее отсекает `Dockerfile.dockerignore`.
- `docker-compose.local.yml` — сервер и PostgreSQL 16 для локальных тестов. Сервер ждёт готовности БД (healthcheck), порт БД открыт только на `127.0.0.1`.
- Переменные окружения берутся из `deployment/.env.dev` (не коммитится); образец — `deployment/.env.example` (коммитится). Сначала скопировать: `cp .env.example .env.dev`.

Запуск — через `Makefile` в корне (при первом запуске он сам создаёт `.env.dev` из примера):

```bash
make dev-compose   # собрать образ и запустить сервер + БД в фоне (http://localhost:8080)
make dev-logs      # логи
make dev-stop      # остановить, данные БД сохраняются
make dev-destroy   # остановить и удалить данные БД
```

Если запускать `docker compose` руками, `--env-file deployment/.env.dev` обязателен: без него compose сразу завершается с ошибкой о незаданных переменных — это касается не только переменных БД, но и `JWT_SECRET`, `ADMIN_LOGIN`, `ADMIN_PASSWORD` сервиса `server`. Вложения сервис пишет в именованный том `files-data`, смонтированный на `/srv/sudv/files`. `CORS_ORIGINS` необязательна: по умолчанию разрешён веб-клиент на `http://localhost:8081` и `http://127.0.0.1:8081`. Браузер присылает адрес так, как его открыли, и для CORS это разные источники; `./kotlin run` печатает вариант с `127.0.0.1`. После правки `CORS_ORIGINS` или пересборки сервера контейнер нужно пересоздать (`make dev-compose`).

### Стек

| Модуль | Библиотеки |
|---|---|
| `client-domain` | `shared:contract`, kotlinx-coroutines (оба экспортируются) |
| `client-data` | Ktor client (движок OkHttp на Android, Js из `ktor-client-core` в браузере; content negotiation + kotlinx-json), DataStore Preferences, Metro; тесты — `ktor-client-mock`, kotlinx-coroutines-test |
| `client-ui` | Compose Multiplatform + Material 3 (Expressive), Decompose (навигация и компоненты), Essenty `lifecycle-coroutines` (`coroutineScope()` компонента), Metro, kotlinx-serialization; тесты — kotlinx-coroutines-test |
| `android-app`, `web-app` | `client-ui`, `client-data`, Metro; `android-app` — ещё `activity-compose` |
| `server` | Ktor server (Netty, YAML-конфиг, content negotiation, status pages, call logging, CORS, auth + JWT, Swagger UI + OpenAPI), Exposed **через R2DBC** (`exposed-r2dbc`, `r2dbc-postgresql`, `r2dbc-pool`), `exposed-kotlin-datetime`, `fastexcel` (запись XLSX), Metro, logback; тесты — Testcontainers + JDBC-драйвер PostgreSQL (только в тестах) |
| `shared:contract` | `shared:domain`, kotlinx-serialization-json, kotlinx-datetime (все экспортируются) |
| `shared:domain` | `kotlinx-serialization-core` (аннотация `@Serializable` на id) и kotlinx-datetime (все экспортируются) |

Версии — только в `libs.versions.toml`, в `module.yaml` ссылки вида `$libs.xxx`. Исключение — Compose: используется встроенный каталог `$compose.*`, потому что его версия привязана к настройке `compose` toolchain. У Ktor `applyBom: false`, чтобы версию задавал только каталог.

### OpenAPI / Swagger

- Swagger UI: `http://localhost:8080/docs`, спецификация: `/docs/documentation.yaml` (отдаётся в JSON). Настройка в `server/src/core/http/SwaggerRoutes.kt`.
- Пути, методы и схемы выводит **плагин компилятора Ktor** (`io.ktor.ktor-compiler-plugin`, опции `openApiEnabled`, `openApiCodeInference`) по коду маршрутов: `call.receive<T>()` / `call.respond<T>()`. В toolchain нет встроенной настройки — плагин подключён вручную в `server/module.yaml` через `compilerPlugins`, как Metro.
- Если вывода не хватает, маршрут дополняется `.describe { … }` (экспериментальное API, `@OptIn(ExperimentalKtorApi::class)`).
- Группы в Swagger UI задаёт KDoc `/** Tag: Employees */` над **каждым** эндпоинтом (`get`/`post`/…). На обёртку его вешать нельзя: на `authenticate` плагин его не читает, а `route("")` возвращает родительский маршрут, и тег достаётся всем группам сразу.
- `SwaggerTest` проверяет, что маршруты попадают в спецификацию и у каждой операции ровно один тег: если плагин перестанет срабатывать или эндпоинт останется без тега, тест упадёт.

### Архитектурные правила

- `shared:contract` и `shared:domain` **не зависят** от Android и Ktor и собираются под `jvm`, `android` и `wasmJs`: любой код в них должен быть common. `contract` зависит от `domain` (и экспортирует его), обратная зависимость запрещена: enum-ы `UserRole`, `PenaltyType`, `PenaltyStatus`, `ReportFormat` живут в `domain`, DTO используют их напрямую.
- Контракт API — `shared:contract`: адреса в `ApiRoutes` (шаблоны `{id}` для сервера, функции для клиента), имена параметров в `ApiParams`, ошибки — `ErrorResponse` + `ErrorCode` (с HTTP-статусом). Даты — `kotlinx.datetime.LocalDate`, моменты времени — `kotlin.time.Instant`, оба в JSON как ISO-строки. Ответы — `*Dto`, тела запросов — `*Request`.
- Идентификаторы сущностей — `@Serializable @JvmInline value class <Entity>Id(val value: Long)` в `shared:domain` (`Ids.kt`), голый `Long` для id не использовать. В JSON id — обычное число. В common-коде `@JvmInline` нужно импортировать явно (`kotlin.jvm.JvmInline`).
- Клиент и сервер общаются только через REST/JSON API, описанное в `shared:contract`.
- Правила предметной области (статусы, переходы, сроки, валидация) пишутся один раз, в `shared:domain`; срок погашения взыскания вычисляется только через `PenaltyRules.expiryDate` — ни в SQL, ни где-либо ещё.
- DI на обеих сторонах — **Metro** (плагин компилятора, граф проверяется при компиляции; модули подключают его через `metro.module-template.yaml`). На сервере граф — `AppGraph`, он создаётся в `Application.module()` через `createGraphFactory<AppGraph.Factory>().create(environment.config, Clock.System)`. У клиентов граф свой у каждой точки входа: `AndroidAppGraph` (хранится в `SudvApplication.graph`, получает `Context`) и `WebAppGraph` (создаётся в `main()`).
- Маршруты сервера регистрируются сами: каждая группа — класс `@Inject @ContributesIntoSet(AppScope::class)`, реализующий `RouteRegistrar` (`core/http/RouteRegistrar.kt`); зависимости приходят в конструктор, `module()` монтирует весь `Set<RouteRegistrar>` из графа. Новая группа маршрутов не требует правок в `AppGraph` и `main.kt`. Плагины Ktor устроены так же: `PluginInstaller` (`core/http/PluginInstaller.kt`) с полем `order`, порядок установки — порядок значений `PluginOrder`; `PluginInstallerTest` проверяет, что у каждого значения ровно один установщик. В `AppGraph` объявлено только то, что нужно `module()`; то, что нужно лишь тестам, вынесено в `AppGraphTestAccessors`.
- Сервер разбит по фичам, фича — компонент из отчёта (гл. 06): `auth` («Управление доступом»), `records` («Учёт сотрудников и нарушений»), `expiry` («Контроль сроков»), `reports` («Отчётность»). Внутри фичи три слоя:
  - `presentation` — маршруты и плагины Ktor;
  - `domain` — сервисы и интерфейсы репозиториев, без Ktor и Exposed;
  - `data` — таблицы Exposed и реализации репозиториев (`@ContributesBinding` к интерфейсу из `domain`).

  `presentation` и `data` зависят от `domain`, но не друг от друга. «Веб-API» из отчёта — это слои `presentation`, «Доступ к данным» — слои `data`.
- Общее для фич лежит в `core/`: `config`, `database` (`Transactor`, создание схемы), `error` (`ApiException`), `files` («Хранилище файлов»), `http` (плагины, `RouteRegistrar`, Swagger), `security` (`CurrentUser`, `requireRole`). `core` от фич не зависит; фичи зависят от `core` и только в одну сторону друг от друга: `auth`, `expiry` и `reports` — от `records`. В корне пакета `main.kt`, `AppGraph` и `Bootstrap` (он затрагивает несколько фич).
- Таблицы каждая фича отдаёт в граф сама (`@ElementsIntoSet` в `data/*Tables.kt`), `createSchema` создаёт весь `Set<Table>`. Поэтому фичу можно вынести в отдельный модуль, не трогая `core`.
- Слой `domain` на сервере не путать с модулем `shared:domain`: там чистые правила (сроки, валидация), здесь сервисы, которые их применяют.
- Права: роль проверяют маршруты (`requireRole`), ограничение `MANAGER` на своё подразделение — сервисы. Вкратце: `ADMIN` управляет учётными записями и может создавать сотрудников (иначе первому кадровику не к кому привязать учётную запись); `HR` работает с сотрудниками, взысканиями, вложениями и отчётами; `MANAGER` только читает сотрудников, взыскания и вложения своего подразделения. `MANAGER` не может указать чужое подразделение даже параметром запроса — такой запрос и обращение по id к чужим данным отклоняются `FORBIDDEN`, а не отфильтровываются молча.
- БД: только Exposed **DSL** (не DAO — `exposed-dao-r2dbc` экспериментальный), все запросы внутри `suspendTransaction { }`. Блокирующий `transaction { }` — это JDBC, не использовать.
- Клиент разбит на модули по слоям, зависимости только такие:
  - `client-ui` → `client-domain` → `shared:contract` → `shared:domain` (`client-domain` экспортирует контракт, поэтому UI видит DTO без прямой зависимости);
  - `client-data` → `client-domain`, `shared:contract`;
  - `android-app`, `web-app` → `client-ui` + `client-data`.

  `client-ui` **не зависит** от `client-data`: UI знает только `IApiClient`, реализацию подключает точка входа. Заодно это нужно Metro — см. «Подводные камни».
- Модели клиента — DTO из `shared:contract`, отдельных доменных моделей на клиенте нет (так и в отчёте, гл. 06: «Клиент API» преобразует ответы «в объекты модуля `shared:contract`»). Отдельные UI-модели заводятся только там, где экрану нужна другая форма данных; маппер DTO → UI-модель лежит в `client-ui` рядом с компонентом экрана.
- «Клиент API» (`KtorApiClient` в `client-data`): по одному методу `IApiClient` на маршрут из `ApiRoutes`. Сам подставляет токен (`ITokenStorage` на DataStore, UI его не видит), ответ с ошибкой превращает в `ApiException(code, message)`, недоступность сервера — в `NetworkException`. На `UNAUTHORIZED` стирает токен, и `isSignedIn` становится `false`. Сессия после перезапуска восстанавливается через `GET /api/auth/me`. `HttpClient` описан один раз в common-коде; платформы (`AndroidDataBindings`, `WebDataBindings`) отдают только движок и `DataStore`.
- Адрес сервера (`ApiConfig`) задаёт точка входа, `client-data` его не знает. Android: строка `api_base_url` в `android-app/res/values/api.xml` (по умолчанию `http://10.0.2.2:8080` — хост из эмулятора), граф получает её через `Context`. Web: `<meta name="sudv-api">` в `web-app/resources/index.html`, читается при запуске; пустое значение — адрес самой страницы (для раздачи веба сервером). BuildKonfig не подходит: это Gradle-плагин, а плагины Kotlin Toolchain пока в preview и плохо поддерживают KMP.
- Тема — `SudvTheme` в `client-ui/src/theme/`: палитра Material 3 сгенерирована Material Color Utilities (SchemeFidelity, спецификация 2021) из `#F57C00`, светлая и тёмная по системе. Под содержимым лежит `Surface` с фоном схемы: без него фон и цвет текста берутся у окна и тема не видна. У Android-окна есть ночной вариант (`res/values-night/themes.xml`). Общие куски экранов — `client-ui/src/common/`: `LoadState` + `load {}`, `LoadStateContent`, формат дат (`dd.MM.yyyy`) и ФИО.
- Room на Android **не используется**: от него отказались намеренно.
- Навигация — Decompose (`client-ui`): `RootComponent` / `DefaultRootComponent`, конфигурации экранов — `@Serializable`. Корень создаётся через `RootComponent.Factory` из графа, дочерние компоненты корень создаёт сам и передаёт им `IApiClient`. Экран — пара `<Экран>Component` (интерфейс + `Default…`, состояние в `Value<State>`) и `<Экран>Content` (Composable), в подпакете `client-ui/src/<экран>/`. Корутины компонента — `coroutineScope()` из Essenty (отменяется вместе с компонентом).
- Оболочка `Main`: разделы роли (`Section`, `sectionsFor(role)` — по матрице прав сервера) в одном `ChildStack`, переключение переносит раздел наверх по равенству (`navigate { filterNot { it == section } + section }`), поэтому ушедший раздел сохраняет компонент и свою историю. Раскладка — `NavigationSuiteScaffold`: нижняя панель или боковая по размеру окна.
- Адреса в вебе — Decompose Web Navigation (`withWebHistory` в `web-app/src/main.kt`, `childStackWebNavigation` в компонентах; опт-ин `ExperimentalDecomposeApi` включён на уровне модулей `client-ui` и `web-app`). Каждый уровень отдаёт свой сегмент пути (`login`, `penalties`, …), Decompose склеивает их через `/`. Начальный адрес разбирается в `DeepLink`; корень хранит его до входа и отдаёт первому `Main`, каждый уровень берёт свой сегмент и передаёт остальное дальше. На Android — `DeepLink.None`.
- Сервер раздаёт собранный веб-клиент из `WEB_DIR` (`core/http/WebClientRoutes.kt`) с fallback на `index.html` для любых путей, кроме `api/` и `docs`. Пустой `WEB_DIR` — только API.
- Раздел — компонент, реализующий `SectionComponent` (и `WebNavigationOwner`, если у его экранов есть адреса); `Main` создаёт его в `section(...)` и отдаёт остаток deep link. Раздел «Сотрудники» (`employees/`): `ChildStack` список → карточка → форма с адресами `/employees`, `/employees/7`, `/employees/new`, `/employees/7/edit`. Открытие из списка заменяет открытую карточку (`navigate { listOf(List, Details(id)) }`), чтобы стек не рос. На ширине от 840 dp (`BoxWithConstraints`) список остаётся слева, справа — верхний экран стека. Что роль может делать в UI, задаёт `main/Permissions.kt` (повторяет матрицу прав сервера).
- Сессия: стек начинается со `Splash`, который при сохранённом токене вызывает `me()` и открывает `Main(user)`; при сбое связи показывает «Повторить». `Login` после входа открывает `Main` с пользователем из `LoginResponse`. Выход из сессии обрабатывает только корень: как только `isSignedIn` становится `false` (выход или отклонённый токен), стек заменяется на `Login`. Ошибки клиента экраны ловят одним `catch (e: ApiClientException)` — общим родителем `ApiException` и `NetworkException`.

### Текущее состояние

Сервер реализует компоненты из отчёта (гл. 06–07): «Веб-API» (слои `presentation`, CORS для веб-клиента), «Управление доступом» (`IAuthService`, JWT, `GET /api/auth/me`, хеширование паролей, bootstrap первого `ADMIN`), «Учёт сотрудников и нарушений» (`IRecordService`), «Контроль сроков» (`IExpiryService` и ежедневный планировщик), «Отчётность» (`IReportService`, экспорт в XLSX/CSV, архив отчётов), «Хранилище файлов» (`IFileStorage`) и «Доступ к данным» (репозитории `I*Repository` на Exposed/R2DBC). Схема БД создаётся при старте, миграций нет. Swagger UI — на `/docs`. Локальный запуск через `deployment/docker-compose.local.yml` и `Makefile` сохраняет данные и файлы вложений в именованных томах между перезапусками.

Клиент разрабатывается по плану `.agents/artifacts/client-plan.md` (фазы 0–8). Готовы фазы 0 (SDK, адрес сервера из точек входа, тема, общие куски экранов), 1 (оболочка с разделами по ролям, адаптивная навигация, адреса страниц в вебе, раздача веба сервером), 2 (справочник сотрудников) и «Клиент API»; веб-клиент собирается в Docker-образ сервера. Остальные разделы пока заглушки. FileKit подключён, но ещё не используется (фаза 5).

Что предстоит реализовать: фазы 1–8 плана клиента; compose-файл для боевого развёртывания (по отчёту: контейнеры JVM 21 и PostgreSQL 16, том `/srv/sudv/files`).

### Подводные камни (уже наступали)

- **JDK 21 для всех модулей** задан в `common.module-template.yaml`. Если собрать `shared/*` под JDK по умолчанию (25), сервер на JVM 21 падает с `UnsupportedClassVersionError`. Шаблон должен подключаться ко **всем** модулям.
- Версию Kotlin задаёт шаблон. Блок `settings` в `project.yaml` toolchain **игнорирует**.
- Compose 1.12 требует `compileSdk: 37`, с 36 сборка падает на проверке AAR-метаданных. `targetSdk` при этом 36 (см. про локальную сеть ниже), `minSdk` 26 — как в отчёте.
- Версии Compose и kotlinx-serialization задаются в `common.module-template.yaml` (`settings.compose.version`, `settings.kotlin.serialization.version`), а не в каталоге. Подняты до 1.12.2 и 1.12.0 под FileKit 0.17.0: иначе часть модулей получает одну версию, часть — другую, и toolchain предупреждает «Declared version … is overridden». `material3-adaptive-navigation-suite` версионируется вместе с Material 3 из `$compose.material3` (сейчас `1.12.0-alpha03`) — при смене версии Compose сверить.
- `ui-tooling` подключён как `runtime-only`. Превью Compose в IDE работает только в **IntelliJ IDEA 2026.2.1+** с актуальным плагином Kotlin Toolchain (исправление KTC-5433). В старых версиях превью не видит runtime-зависимости.
- В тестах Ktor модули из конфига загружаются только через `testApplication { configure("application.yaml") }`, иначе маршрутов нет (404).
- `executable-jar` содержит `logback.xml` дважды, поэтому в `logback.xml` стоит `NopStatusListener`, иначе logback при каждом старте печатает свой статус. Есть известный баг toolchain 0.13: `executable-jar` может терять зависимости с одинаковыми именами файлов. Если после обновления зависимостей появится `NoClassDefFoundError` только в jar или в Docker, проверять это в первую очередь.
- `kotlinx-datetime` на сервере разрешается в вариант `0.8.0-0.6.x-compat` (его требует `exposed-kotlin-datetime`). `LocalDate` и его сериализатор в нём есть, это не ошибка.
- Плагины компилятора (Metro) и KSP-процессоры подключаются в `settings.kotlin.compilerPlugins` / `settings.kotlin.ksp`, `$libs`-ссылки там работают. Что плагин реально сработал, проверять по сгенерированным классам (`AppGraph$Impl`), а не по успешной сборке.
- Ktor 3.6.1 кодирует `filename*` в `ContentDisposition` без percent-encoding не-ASCII символов, поэтому заголовок для вложений собирается вручную в `core/http/ContentDisposition.kt`.
- `receiveMultipart(formFieldLimit)` ограничивает и файловые части; превышение лимита обрывает запрос необрабатываемым `500` вместо ответа с кодом ошибки. Поэтому размер вложения проверяется по `Content-Length` и ограниченным чтением файловой части, а не лимитом Ktor.
- `fastexcel` (через `opczip`) пишет нестандартный data descriptor: `ZipInputStream` принимает его за несовпадение размера, хотя архив корректный. Тесты читают XLSX через `ZipFile` (центральный каталог) вместо `ZipInputStream`.
- В `testApplication` модули из конфига загружаются автоматически при первом HTTP-вызове; если тест обращается к БД до него (например, напрямую к `AppGraph`), нужен явный `startApplication()`.
- Metro собирает `@ContributesTo`/`@ContributesBinding` только из модулей, которые лежат в **compile classpath** модуля с `@DependencyGraph`. Неэкспортированная зависимость транзитивно туда не попадает: когда `client-data` был подключён только к `client-ui`, графы его не видели, а сборка падала с `MissingBinding` лишь после того, как граф запрашивал что-то из `client-data`. Поэтому модуль с реализациями подключается к точке входа напрямую.
- Реализации в клиентских модулях (`KtorApiClient`, `DataStoreTokenStorage`, `DefaultRootComponentFactory`) — `internal`. Граф в другом модуле видит их благодаря опции Metro `generate-contribution-providers` (`metro.module-template.yaml`): Metro генерирует рядом публичный провайдер, и граф вызывает его.
- **Модуль с графом должен видеть все типы из сигнатур привязок**, поэтому `client-data` экспортирует DataStore и `ktor-client-core`. Без экспорта Android собирается (запуском не проверяли), а на Kotlin/Wasm сборка была зелёной, но страница падала при загрузке с `IrLinkageError: … No function found for symbol '…ProvideDataStoreTokenStorageAsITokenStorageMetroFactory.Companion.create|create(dev.zacsweers.metro.Provider<androidx.datastore.core.DataStore>){}[0]'`. Причина: Wasm связывает модули по IR-сигнатурам, а сигнатуру вызова вычисляет модуль с графом. Не видя DataStore, `web-app` записал параметр как `Provider<DataStore>` без аргумента `<Preferences>`, а в klib `client-data` функция объявлена с `Provider<DataStore<Preferences>>`. На JVM сигнатура стёрта до `Provider` и совпадает. Типы Ktor без экспорта линкуются, но только по совпадению: у `HttpClient` и `HttpClientEngine` нет аргументов, терять нечего. Экспортируем их всё равно, чтобы правило было общим. Без `generate-contribution-providers` Metro сам ловит такую ошибку при сборке (`Provider<out <error>>`), с ней — молча пропускает.
- Поэтому в `web-app` стоит `-Xpartial-linkage-loglevel=ERROR`. По умолчанию компилятор klib-платформ не роняет сборку на ненайденном вызове, а заменяет его заглушкой, которая бросает `IrLinkageError` при выполнении («частичная линковка»). С флагом та же ошибка ломает `./kotlin build` на этапе линковки, с точным символом.
- Child Panels Decompose при смене панелей **заменяет** запись в истории браузера: «Назад» в браузере ушёл бы со страницы, а не закрыл карточку. Поэтому разделы — обычный `ChildStack` (он добавляет запись на каждый экран), а «список рядом с карточкой» на широком окне раскладывается вручную.
- Toolchain не может сам вывести версию Material 3 для Compose 1.12.2 (`$compose.material3` → предупреждение «cannot be reliably determined», видно только в полном выводе сборки). Material 3 подключён явно из каталога: `compose-material3` в `libs.versions.toml`.
- Сборка веб-клиента в Docker: pnpm, через который toolchain ставит npm-зависимости, требует системную `libatomic1`; в `eclipse-temurin:*-jdk-noble` её нет, поставлена в сборочной стадии.
- `bringToFront` в Decompose убирает из стека **все конфигурации того же класса**, а не равные. Для разделов-значений одного enum это уничтожало остальные разделы вместе с историей; переносим по равенству через `navigate`.
- В `index.html` нужны `<meta name="viewport">` (без него мобильный браузер верстает страницу шириной 980 px, и Material выбирает десктопную раскладку) и `<base href="/">` (`{{kotlin.scripts}}` подставляет относительные пути; на `/penalties/42` скрипты искались бы в `/penalties/`).
- Dev-сервер `./kotlin run -m web-app` не отдаёт `index.html` на глубокие пути: перезагрузка на `/penalties` даёт 404. Настройки для этого у toolchain нет; для проверки адресов веб-клиент запускается через сервер (`WEB_DIR`, см. «Сборка и запуск»). Адрес в браузере обновляется не мгновенно, а после перехода.
- `NavigationSuiteScaffold` показывает нижнюю панель не только на узком, но и на низком окне (телефон в альбомной ориентации) — это правило Material, не ошибка.
- Иконки — Material Symbols из репозитория Google (`symbols/android/<имя>/materialsymbolsoutlined/<имя>_24px.xml`) в `client-ui/composeResources/drawable/`: библиотеки `material-icons-*` для Compose Multiplatform застыли на 1.7.3. Из XML убрать `android:tint="?attr/..."` и заменить `@android:color/white` на `#FFFFFFFF`: Compose Multiplatform не знает Android-атрибутов темы.
- Ktor CIO собирается под wasmJs, но его сокеты работают только в Node.js (`node:net`), в браузере первый же запрос падает. В браузере движок — `Js` (Fetch API); для wasm он входит в `ktor-client-core`, а артефакт `ktor-client-js` под wasm — пустая заглушка (подключать не нужно, toolchain на него ругается `KLIB loader: Not a Kotlin library`).
- Android по умолчанию запрещает HTTP без TLS. Для локального сервера разрешение дано только для `10.0.2.2` (хост-машина из эмулятора) и `localhost` в `android-app/res/xml/network_security_config.xml`.
- На Wasm движок Ktor `Js` сообщает о неудачном `fetch` (сервер недоступен, отказ CORS) через `kotlin.Error("Fail to fetch")`, а это не `Exception`. Поэтому `KtorApiClient` превращает в `NetworkException` любой `Throwable`, кроме отмены корутины. Когда там ловился только `Exception`, ошибка проходила мимо экрана, и кнопка входа крутилась бесконечно. Тест `failedFetchOnWasmBecomesNetworkException` воспроизводит именно это.
- **Android 17 (API 37) закрывает приложениям локальную сеть**, если `targetSdk` ≥ 37 и не выдано runtime-разрешение `ACCESS_LOCAL_NETWORK` (группа `NEARBY_DEVICES`). Сюда попадает и `10.0.2.2` (хост из эмулятора), и сервер в сети предприятия. TCP не получает ошибку, а уходит в таймаут — клиент показывает «Нет связи с сервером»; DNS на порту 53 — исключение. Поэтому `targetSdk: 36`: приложениям ниже 37 доступ даётся вместе с `INTERNET`, а `ACCESS_LOCAL_NETWORK` документация просит не объявлять. При переходе на 37 нужен запрос разрешения во время работы. Проверить доступ под UID приложения: `adb shell "run-as ru.haykor.sudv sh -c 'nc -z -w 4 10.0.2.2 8080 && echo OK'"`.
- Toolchain не замечает правку `android-app/src/AndroidManifest.xml`: сборка проходит, а в APK остаётся старый манифест (`build/tasks/_android-app_buildAndroidDebug/.../merged_manifest` со старой датой). После правки манифеста удалить `build/tasks/_android-app_*` и собрать заново. Проверить итог: `aapt2 dump permissions <apk>`.
- Без `exposeHeader(Content-Disposition)` в CORS браузер прячет этот заголовок от скрипта, и веб-клиент не узнаёт имя скачанного файла.
- Wasm-тесты (`./kotlin test -m client-data`) запускаются в Chromium через Playwright, которого toolchain ставит через pnpm. Если выше по дереву каталогов есть `pnpm-workspace.yaml` (например, в `~`), pnpm ставит пакеты туда, а не в `build/`, и тесты падают с `Failed to install Chromium browser`. Тесты на Android при этом проходят.
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
- в таблице программных средств (гл. 06) нет Decompose и Metro;
- в отчёте клиент — один модуль `android`, в коде — `client-domain`, `client-data`, `client-ui` и точки входа `android-app`, `web-app`; веб-клиента (`web-app`) и DataStore в отчёте нет.

## Git

- Сообщения коммитов: `feat: …`, `chore: …` (Conventional Commits, на английском, со строчной буквы).
- Не коммитить `build/`, `.idea/` и `.env*`, кроме `.env.example` (есть в `.gitignore`).
