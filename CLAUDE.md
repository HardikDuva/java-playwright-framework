# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

End-to-end test automation framework using **Playwright + Java + TestNG** with parallel execution, per-thread browser reuse, trace/screenshot on failure, layered configuration, an API testing layer, and GitHub Actions CI. Tests run against [practicesoftwaretesting.com](https://practicesoftwaretesting.com).

---

## Commands

### Run the default suite
```bash
mvn clean test
```

### Run a named suite (by group)
```bash
mvn clean test -DsuiteXmlFile=src/test/resources/suites/smoke.xml
mvn clean test -DsuiteXmlFile=src/test/resources/suites/regression.xml
mvn clean test -DsuiteXmlFile=src/test/resources/suites/api.xml
```

### Common overrides
```bash
mvn clean test -Dbrowser.name=firefox -Dbrowser.headless=false   # engine / headed
mvn clean test -Denv=staging                                     # load config-staging.properties overlay
mvn clean test -DthreadCount=6 -DtestFailureIgnore=false         # parallelism / quality gate
```

### Compile only (no tests)
```bash
mvn clean compile test-compile
```

### Install Playwright browsers (first-time setup)
```bash
mvn exec:java -Dexec.args="install chromium"      # exec-maven-plugin is pre-configured
# CI/Linux: mvn exec:java -Dexec.args="install --with-deps chromium"
```

### Allure report
```bash
mvn allure:serve
# or: allure generate target/allure-results --clean -o target/allure-report && allure open target/allure-report
```

### BrowserStack (optional remote execution)
```bash
export BR_USERNAME=<browserstack-username>
export BR_ACCESS_KEY=<browserstack-access-key>
# Then set REMOTE_PROVIDER=browserstack (config.properties or -DREMOTE_PROVIDER=browserstack)
```

---

## Architecture

```
TestNG Suite (smoke.xml / regression.xml / api.xml / LoginTests.xml)
        │  parallel="methods", thread-count="4"
        ▼
BaseTest  (@BeforeSuite / @BeforeMethod / @AfterMethod / @AfterSuite)
        │  PlaywrightFactory → per-thread Playwright + Browser (REUSED across tests)
        │  per test: new BrowserContext + Page; tracing().start()
        │  ThreadLocal<BrowserContext, Page, Pages>; sets MDC "test" for logging
        │  commonPages() → lazy Pages instance per thread
        ▼
Pages  (com.portal.pages.Pages)
        │  Lazy cache — instantiates page objects via reflection (constructor(Page))
        │  loginPage() · dashboardPage() · homePage()
        ▼
Page Objects  (extend BasePage)
        │  LoginPage · DashboardPage · UserHomePage
        ▼
@Test Methods  (call page objects, assert with BaseTest.assertTrue)
        │
        ▼
On failure  → screenshot + Playwright trace (target/traces) attached to Allure
Listeners   → AllureEnvironmentListener writes environment.properties + categories.json
```

**Key lifecycle change:** `Playwright` and `Browser` are created **once per thread** by `PlaywrightFactory` and reused; only `BrowserContext`/`Page` are per-test (state isolation is at the context boundary). All browsers are disposed once in `BaseTest.@AfterSuite` → `PlaywrightFactory.disposeAll()`.

**No Steps layer.** Tests call page objects directly. An **API layer** (`ApiClient`) runs alongside the UI layer for browser-free tests and API-driven setup.

**No automatic retry** — a failed test is final on the first attempt.

---

## Key Classes

| Class | Package | Role |
|---|---|---|
| `BaseTest` | `com.base` | Test lifecycle; per-test context/page; trace/screenshot on failure; `getPage()`, `commonPages()`, `assertTrue()` |
| `BasePage` | `com.base` | Playwright `Page` wrapper — `click`, `fill`, `getText`, `waitUntilLocatorVisible`, `isLocatorVisible`, `assertVisible` (web-first), `locator`, `waitForAllLoadersToDisappear`, `waitForURLToLoad`, `uploadFile`; SLF4J logging |
| `PlaywrightFactory` | `com.driver` | Per-thread `Playwright`/`Browser` reuse (chromium/firefox/webkit); global `disposeAll()` |
| `AllureListener` | `com.base` | `addTestStep`, `captureAndAttachScreenshot(Page)`, `attachFile(name, path, mime)` |
| `AllureEnvironmentListener` | `com.listeners` | `ISuiteListener` — writes `environment.properties` + copies `categories.json` (auto-registered) |
| `StringValidator` | `com.base` | Static helpers: `isValidEmail`, `isValidPhone`, `isValidZipCode` |
| `Pages` | `com.portal.pages` | Aggregator with typed accessors; `ConcurrentHashMap` + reflection for lazy instantiation |
| `LoginPage` | `com.portal.pages` | Login form actions and visibility probes |
| `DashboardPage` | `com.portal.pages.admin` | `waitForAdminPortal()` |
| `UserHomePage` | `com.portal.pages.user` | `waitForHomePage()` |
| `ApiClient` | `com.api` | Playwright `APIRequestContext` wrapper — `get`/`post`, `loginAndGetToken`, JSON field extraction; `AutoCloseable` |
| `DataFactory` | `com.data` | Datafaker-based unique users/emails/passwords (parallel-safe) → `TestUser` record |
| `ConfigReader` | `com.config` | Layered config with precedence (see below); typed getters; thread-safe cache |
| `UserRole` | `com.enums` | `ADMIN`, `USER` |
| `PCBrowserStackCapabilities` | `com.capabilities` | Builds BrowserStack CDP WebSocket URL; Chrome, Edge, Safari |

---

## Configuration

**`src/test/resources/config/config.properties`** — base file. Override per environment with `config/config-<env>.properties` (selected via `-Denv=<name>`).

Keys: `BASE_URL`, `API_BASE_URL`, `REMOTE_PROVIDER` (`local`/`browserstack`), `browser.name` (`chromium`/`firefox`/`webkit`), `browser.headless`, `trace.enabled`, `video.on.failure`, `visibility.wait.timeout`, `navigation.wait.timeout`, `admin.login.*`, `user.login.*`.

### Resolution precedence (highest wins)
```
1. JVM system property        -Dbrowser.name=firefox
2. OS environment variable    BROWSER_NAME=firefox   (key upper-cased, '.' -> '_')
3. Environment overlay file   config/config-<env>.properties
4. Base file                  config/config.properties
5. Hard-coded default
```

Secrets should come from (1) or (2) in CI (e.g. `ADMIN_LOGIN_PASSWORD`, `USER_LOGIN_PASSWORD`) so they never live in git. The committed credentials are the site's public demo accounts.

---

## Test Organization (groups & suites)

Tests are tagged with TestNG `groups`; suites filter by group.

| Group | Suite file | Purpose |
|---|---|---|
| `smoke` | `suites/smoke.xml` | Critical happy-path (fast) |
| `regression` | `suites/regression.xml` | Full UI coverage |
| `api` | `suites/api.xml` | REST-level tests, no browser |

The default suite `src/test/resources/TestSuite/LoginTests.xml` runs the `LoginTests` class directly. All UI suites are `parallel="methods"`, `thread-count="4"`.

Suite parameters (used by BrowserStack): `OS`, `OSVersion`, `Device`, `Browser`, `BrowserVersion`.
- `OS`/`OSVersion` — desktop OS capability. `Browser`/`BrowserVersion` — browser selection. `Device` — present but unused.
- All are `@Optional` in `BaseTest.setUpSuite`, so suites without these parameters (e.g. `api.xml`) run fine.

---

## Writing New Tests

1. Create a test class in `com.portal.tests`, extend `BaseTest`.
2. Tag it: `@Test(groups = {"smoke", "regression"})` (add Allure `@Epic`/`@Feature`/`@Severity` as useful).
3. Access pages via `commonPages().<page>().<action>()`.
4. Assert with `assertTrue(condition, message)` — captures a screenshot on failure.
5. For unique data use `DataFactory.newUser()`; for API setup use `try (ApiClient api = new ApiClient()) { ... }`.

### Adding a new page object

- Create the class in `com.portal.pages.*`, extend `BasePage`.
- Provide a `constructor(Page page)` — `Pages` uses reflection for instantiation.
- Add a typed accessor method to `com.portal.pages.Pages`.
- Prefer `assertVisible(selector)` (web-first, auto-retrying) over boolean probes for the primary assertion.

---

## Reporting & Diagnostics

- Failure → full-page screenshot + Playwright trace (`target/traces/<test>.zip`) attached to Allure. Open a trace with `npx playwright show-trace <file>`.
- Optional video (`video.on.failure=true`), kept only on failure.
- `AllureEnvironmentListener` writes `environment.properties` and `categories.json` into `target/allure-results`.
- Logging: SLF4J + Logback (`src/test/resources/logback.xml`), console + `target/logs/test-run.log`, with per-thread `%X{test}` (MDC) name.

---

## CI/CD

- `.github/workflows/ci.yml` — PR/push to `master`: compile → install Chromium → run **smoke** suite headless (merge gate) → upload Allure results + traces.
- `.github/workflows/nightly.yml` — scheduled/manual: **regression** across a `chromium`/`firefox`/`webkit` matrix.
- Quality gate: Surefire `testFailureIgnore=false` by default (a failing test fails the build). Override with `-DtestFailureIgnore=true` only for diagnostic runs.

---

## Commit Conventions

- **Format:** `<type>(<scope>): <description>` — types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`
- **Max 50 chars**, imperative mood, no trailing period
- **Branch format:** `<type>/<short-description>` — e.g., `feat/add-product-page-tests`
