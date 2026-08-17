# Playwright Java Demo Framework

An end-to-end test automation framework built with **Playwright + Java + TestNG**, featuring parallel execution, per-thread browser reuse, trace/screenshot capture on failure, layered configuration, an API testing layer, and CI/CD via GitHub Actions. Tests run against [practicesoftwaretesting.com](https://practicesoftwaretesting.com) — a public practice site purpose-built for test automation learning.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Test Framework | TestNG | 7.12.0 |
| Browser Automation | Playwright | 1.61.0 |
| Remote Execution | BrowserStack (CDP) | optional |
| Reporting | Allure | 2.35.3 |
| Logging | SLF4J + Logback | 1.5.18 |
| Test Data | Datafaker | 2.4.4 |
| Build Tool | Maven | 3 |

---

## Prerequisites

1. **Java 21** — set `JAVA_HOME` after installing.
2. **Maven 3** — `brew install maven` on macOS or follow the [official guide](https://maven.apache.org/install.html).
3. Install Playwright browsers on first run:
   ```bash
   mvn exec:java -Dexec.args="install chromium"
   ```
   (The `exec-maven-plugin` is pre-configured, so the long `-Dexec.mainClass=...` form is no longer needed. Use `install --with-deps chromium` in CI/Linux.)

---

## Project Structure

```
src/test/java/com/
├── base/
│   ├── BaseTest.java           # Test lifecycle: context/page per test, trace/screenshot on failure, MDC logging
│   ├── BasePage.java           # Playwright helpers: click, fill, waits, assertVisible (web-first), uploadFile
│   ├── AllureListener.java     # Allure screenshot + file (trace/video) attachment helpers
│   └── StringValidator.java    # Static helpers: isValidEmail, isValidPhone, isValidZipCode
│
├── driver/
│   └── PlaywrightFactory.java  # Per-thread Playwright + Browser reuse; global dispose at suite end
│
├── capabilities/
│   └── PCBrowserStackCapabilities.java   # BrowserStack CDP connection for desktop browsers
│
├── config/
│   └── ConfigReader.java       # Layered config: system-prop > env var > env overlay > base file > default
│
├── api/
│   └── ApiClient.java          # Playwright APIRequestContext wrapper (get/post, loginAndGetToken)
│
├── data/
│   ├── DataFactory.java        # Datafaker-based unique users/emails/passwords (parallel-safe)
│   └── TestUser.java           # Immutable test-user record
│
├── listeners/
│   └── AllureEnvironmentListener.java   # Writes environment.properties + copies categories.json
│
├── enums/
│   └── UserRole.java           # ADMIN, USER
│
└── portal/
    ├── pages/
    │   ├── Pages.java          # Lazy page-object aggregator (loginPage, dashboardPage, homePage)
    │   ├── LoginPage.java      # Login form interactions
    │   ├── admin/DashboardPage.java   # Admin portal wait
    │   └── user/UserHomePage.java     # User account page wait
    └── tests/
        ├── LoginTests.java     # 9 UI login scenarios, tagged smoke/regression
        └── api/LoginApiTests.java     # API-level login tests (group: api)

src/test/resources/
├── config/
│   └── config.properties       # Base config (override per env with config-<env>.properties)
├── suites/
│   ├── smoke.xml               # group: smoke
│   ├── regression.xml          # group: regression
│   └── api.xml                 # group: api
├── TestSuite/
│   └── LoginTests.xml          # Default suite (runs the LoginTests class)
├── allure/
│   └── categories.json         # Failure classification buckets
├── META-INF/services/
│   └── org.testng.ITestNGListener   # Auto-registers AllureEnvironmentListener
└── logback.xml                 # Console + file logging with per-thread MDC test name

.github/workflows/
├── ci.yml                      # PR smoke gate
└── nightly.yml                 # Nightly cross-browser regression matrix
```

---

## Configuration

**`src/test/resources/config/config.properties`**

| Key | Description | Default |
|---|---|---|
| `BASE_URL` | Target application URL | `https://practicesoftwaretesting.com` |
| `API_BASE_URL` | REST API base URL | `https://api.practicesoftwaretesting.com` |
| `REMOTE_PROVIDER` | `local` or `browserstack` | `local` |
| `browser.name` | Local engine: `chromium` / `firefox` / `webkit` | `chromium` |
| `browser.headless` | Run browser headless | `true` |
| `trace.enabled` | Capture Playwright trace (saved on failure) | `true` |
| `video.on.failure` | Record video, kept only on failure | `false` |
| `visibility.wait.timeout` | Element visibility timeout (ms) | `10000` |
| `navigation.wait.timeout` | Page navigation timeout (ms) | `30000` |
| `admin.login.email` / `admin.login.password` | Admin credentials | public demo creds |
| `user.login.email` / `user.login.password` | User credentials | public demo creds |

### Configuration precedence (highest wins)

```
1. JVM system property        -Dbrowser.name=firefox
2. OS environment variable    BROWSER_NAME=firefox   (key upper-cased, '.' -> '_')
3. Environment overlay file   config/config-<env>.properties   (via -Denv=staging)
4. Base file                  config/config.properties
5. Hard-coded default
```

Secrets (passwords) should be supplied via a system property or env var in CI so
they never live in git, e.g. `ADMIN_LOGIN_PASSWORD` / `USER_LOGIN_PASSWORD`.

---

## Running Tests

### Default suite (Login Tests — 4 parallel threads)
```bash
mvn clean test
```

### Named suites (by group)
```bash
# Smoke (PR gate)
mvn clean test -DsuiteXmlFile=src/test/resources/suites/smoke.xml

# Full regression
mvn clean test -DsuiteXmlFile=src/test/resources/suites/regression.xml

# API-only (no browser)
mvn clean test -DsuiteXmlFile=src/test/resources/suites/api.xml
```

### Common overrides
```bash
# Run on Firefox, headed
mvn clean test -DsuiteXmlFile=src/test/resources/suites/regression.xml \
  -Dbrowser.name=firefox -Dbrowser.headless=false

# Target another environment (loads config-staging.properties)
mvn clean test -Denv=staging

# Tune parallelism / quality gate
mvn clean test -DthreadCount=6 -DtestFailureIgnore=false
```

### Compile only (no tests)
```bash
mvn clean compile test-compile
```

---

## Parallel Execution

The suites use `parallel="methods"` with `thread-count="4"` — each `@Test` method
runs in its own thread. **`PlaywrightFactory` creates one `Playwright` + `Browser`
per thread and reuses them across tests on that thread**; only the cheap
`BrowserContext` + `Page` are created per test, which is what isolates state. All
created browsers are disposed once in `@AfterSuite`.

```
Thread 1 ─┐  Playwright + Browser (reused)
          ├─ Context+Page → Test A → close context
          └─ Context+Page → Test B → close context
Thread 2 ─┐  Playwright + Browser (reused)
          ├─ Context+Page → Test C → close context
          └─ Context+Page → Test D → close context
```

---

## Architecture

```
TestNG Suite (smoke.xml / regression.xml / api.xml / LoginTests.xml)
        │  parallel="methods", thread-count="4"
        ▼
BaseTest  (@BeforeSuite / @BeforeMethod / @AfterMethod / @AfterSuite)
        │  PlaywrightFactory → per-thread Playwright + Browser (reused)
        │  per test: new Context + Page, tracing started
        │  commonPages() → lazy Pages instance per thread
        ▼
Pages  (aggregator — lazy-initialises page objects via reflection)
        │  loginPage() · dashboardPage() · homePage()
        ▼
Page Objects  (extend BasePage — click/fill/waits, web-first assertVisible)
        │  LoginPage · DashboardPage · UserHomePage
        ▼
@Test Methods  (call page objects, assert outcomes)
        │
        ▼
On failure → screenshot + Playwright trace (target/traces) attached to Allure
Listeners  → AllureEnvironmentListener writes environment.properties + categories.json
```

An **API layer** (`ApiClient` over Playwright's `APIRequestContext`) runs alongside
the UI layer for fast, browser-free tests and for setting up state via REST.

---

## Test Organization (groups & tags)

Tests are tagged with TestNG `groups` so suites can slice them:

| Group | Purpose | Suite file |
|---|---|---|
| `smoke` | Critical happy-path checks (fast) | `suites/smoke.xml` |
| `regression` | Full UI coverage | `suites/regression.xml` |
| `api` | REST-level tests, no browser | `suites/api.xml` |

Allure metadata (`@Epic`, `@Feature`, `@Severity`) is applied on test classes/methods.

---

## Writing New Tests

1. Create a test class in `com.portal.tests`, extend `BaseTest`.
2. Tag it: `@Test(groups = {"smoke", "regression"})`.
3. Access page objects via `commonPages().<page>().<action>()`.
4. Use `assertTrue(condition, message)` — it captures a screenshot on failure.

```java
public class MyTests extends BaseTest {

    @Test(groups = {"regression"})
    public void exampleTest() {
        commonPages().loginPage().navigateToLogin();
        assertTrue(
            commonPages().loginPage().isLoginFormVisible(),
            "Login form must be visible"
        );
    }
}
```

### Adding a new page object

1. Create a class in `com.portal.pages.*`, extend `BasePage`.
2. Add a `constructor(Page page)` — `Pages` uses reflection to instantiate it.
3. Add a typed accessor to `com.portal.pages.Pages`.

```java
public class MyPage extends BasePage {

    private static final String MY_ELEMENT = "[data-test='my-element']";

    public MyPage(Page page) {
        super(page);
    }

    public void assertLoaded() {
        assertVisible(MY_ELEMENT);   // web-first, auto-retrying assertion
    }
}
```

### Using test data

```java
TestUser user = DataFactory.newUser();   // unique email/password, parallel-safe
```

### API tests / API-driven setup

```java
try (ApiClient api = new ApiClient()) {
    String token = api.loginAndGetToken(email, password);
    // ... use token, or seed data via api.post(...)
}
```

---

## BrowserStack (optional)

Set `REMOTE_PROVIDER=browserstack` (via config or `-DREMOTE_PROVIDER=browserstack`)
and export credentials:

```bash
export BR_USERNAME=<your-browserstack-username>
export BR_ACCESS_KEY=<your-browserstack-access-key>
```

`PCBrowserStackCapabilities` builds the CDP WebSocket URL and connects. Supported
browsers: Chrome, Edge, Safari (webkit). The `OS`/`OSVersion` suite parameters
select the desktop OS; `Device` is present in the XML but unused.

---

## Reporting

Failed tests automatically capture a full-page **screenshot** and, when tracing is
enabled, a **Playwright trace** saved to `target/traces/` and attached to Allure.
`AllureEnvironmentListener` also writes `environment.properties` (URLs, browser,
env, Java, OS) and `categories.json` (failure buckets) into the results.

```bash
# Generate + serve a report from the latest run
mvn allure:serve

# Or with the standalone Allure CLI
allure generate target/allure-results --clean -o target/allure-report
allure open target/allure-report
```

Open a saved trace with:
```bash
npx playwright show-trace target/traces/<file>.zip
```

Allure results are written to `target/allure-results/` during the run; logs to
`target/logs/test-run.log`.

---

## CI/CD (GitHub Actions)

| Workflow | Trigger | What it runs |
|---|---|---|
| `.github/workflows/ci.yml` | PR / push to `master` | Smoke suite (headless Chromium) as a merge gate; uploads Allure results + traces |
| `.github/workflows/nightly.yml` | Nightly (02:00 UTC) / manual | Regression across a `chromium` / `firefox` / `webkit` matrix |

The build quality gate is enforced by Surefire `testFailureIgnore=false` (default),
so a failing test fails the build. Override with `-DtestFailureIgnore=true` only for
local diagnostic runs.

---

## Notes

- **No automatic retry** — a failed test is final on the first attempt.
- Configuration secrets should come from env/CI, not the committed properties file
  (the demo credentials shipped here are the site's public demo accounts).
