# Playwright Java Demo Framework

A demo end-to-end test automation framework showing how to build a **Playwright + Java + TestNG** project with parallel test execution. Tests run against [practicesoftwaretesting.com](https://practicesoftwaretesting.com) — a public practice site purpose-built for test automation learning.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Test Framework | TestNG | 7.12.0 |
| Browser Automation | Playwright | 1.61.0 |
| Remote Execution | BrowserStack (CDP) | optional |
| Reporting | Allure | 2.35.3 |
| Build Tool | Maven | 3 |

---

## Prerequisites

1. **Java 21** — set `JAVA_HOME` after installing.
2. **Maven 3** — `brew install maven` on macOS or follow the [official guide](https://maven.apache.org/install.html).
3. Install Playwright browsers on first run:
   ```bash
   mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium" -D exec.classpathScope=test
   ```

---

## Project Structure

```
src/test/java/com/
├── base/
│   ├── BaseTest.java           # @BeforeSuite/@BeforeMethod/@AfterMethod — ThreadLocal browser lifecycle
│   ├── BasePage.java           # Playwright helpers: click, fill, wait, getText, uploadFile
│   ├── AllureListener.java     # Allure screenshot utility (called on assertion failure)
│   └── StringValidator.java   # Static helpers: isValidEmail, isValidPhone, isValidZipCode
│
├── capabilities/
│   └── PCBrowserStackCapabilities.java   # BrowserStack CDP connection for desktop browsers
│
├── config/
│   └── ConfigReader.java       # Reads config/config.properties — URLs, credentials, flags
│
├── enums/
│   └── UserRole.java           # ADMIN, USER
│
└── portal/
    ├── pages/
    │   ├── Pages.java          # Lazy page object aggregator (loginPage, dashboardPage, homePage)
    │   ├── LoginPage.java      # Login form interactions
    │   ├── admin/
    │   │   └── DashboardPage.java    # Admin portal wait
    │   └── user/
    │       └── UserHomePage.java     # User account page wait
    └── tests/
        └── LoginTests.java   # 9 login test scenarios (all enabled)

src/test/resources/
├── config/
│   └── config.properties       # BASE_URL, credentials, REMOTE_PROVIDER, headless flag
└── TestSuite/
    └── LoginTests.xml          # TestNG suite — parallel="methods" thread-count="4"
```

---

## Configuration

**`src/test/resources/config/config.properties`**

| Key | Description | Default |
|---|---|---|
| `BASE_URL` | Target application URL | `https://practicesoftwaretesting.com` |
| `REMOTE_PROVIDER` | `local` or `browserstack` | `local` |
| `browser.headless` | Run browser headless | `false` |
| `admin.login.email` | Admin credentials | `admin@practicesoftwaretesting.com` |
| `admin.login.password` | Admin password | `welcome01` |
| `user.login.email` | User credentials | `customer2@practicesoftwaretesting.com` |
| `user.login.password` | User password | `welcome01` |
| `visibility.wait.timeout` | Element visibility timeout (ms) | `10000` |
| `navigation.wait.timeout` | Page navigation timeout (ms) | `30000` |

---

## Running Tests

### Default suite (Login Tests — 4 parallel threads)
```bash
mvn clean test
```

### Run a specific TestNG XML suite
```bash
mvn clean test -DsuiteXmlFile=src/test/resources/TestSuite/LoginTests.xml
```

### Compile only (no tests)
```bash
mvn clean compile test-compile
```

### View Allure report after run
```bash
mvn allure:serve
```

---

## Parallel Execution

The suite uses `parallel="methods"` with `thread-count="4"` — each `@Test` method runs in its own thread with a dedicated browser instance. Playwright objects (`Playwright`, `Browser`, `BrowserContext`, `Page`) are stored in `ThreadLocal` fields in `BaseTest`, ensuring complete isolation between concurrent tests.

```
Thread 1 → Playwright → Browser → BrowserContext → Page  →  Test A
Thread 2 → Playwright → Browser → BrowserContext → Page  →  Test B
Thread 3 → Playwright → Browser → BrowserContext → Page  →  Test C
Thread 4 → Playwright → Browser → BrowserContext → Page  →  Test D
```

---

## Architecture

```
TestNG XML Suite (LoginTests.xml)
        │  parallel="methods", thread-count="4"
        ▼
BaseTest  (@BeforeSuite / @BeforeMethod / @AfterMethod)
        │  ThreadLocal<Playwright, Browser, BrowserContext, Page>
        │  commonPages() → lazy Pages instance per thread
        ▼
Pages  (aggregator — lazy-initialises page objects via reflection)
        │  loginPage() · dashboardPage() · homePage()
        ▼
Page Objects  (extend BasePage)
        │  LoginPage · DashboardPage · UserHomePage
        ▼
@Test Methods  (call page objects, assert outcomes)
        │
        ▼
AllureListener  (screenshot on failure → attached to Allure report)
```

---

## Writing New Tests

1. Create a test class in `com.portal.tests`, extend `BaseTest`.
2. Access page objects via `commonPages().<page>().<action>()`.
3. Use `assertTrue(condition, message)` — it captures a screenshot on failure before failing.
4. Add the class to `LoginTests.xml` (or create a new XML suite).

Example:
```java
public class MyTests extends BaseTest {

    @Test
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

    public boolean isMyElementVisible() {
        return isLocatorVisible(MY_ELEMENT);
    }
}
```

---

## BrowserStack (optional)

Set `REMOTE_PROVIDER=browserstack` in `config.properties` and export credentials:

```bash
export BR_USERNAME=<your-browserstack-username>
export BR_ACCESS_KEY=<your-browserstack-access-key>
```

`PCBrowserStackCapabilities` builds the CDP WebSocket URL and connects via `playwright.chromium().connect(wsUrl)`. Supported browsers: Chrome, Edge, Safari (webkit).

The `OS` and `OSVersion` suite parameters are passed to BrowserStack to select the desktop OS. The `Device` parameter is present in the XML for structural completeness but is not consumed by any capability class.

---

## Reporting

Failed tests automatically capture a full-page screenshot via `AllureListener.captureAndAttachScreenshot()`, which is called from `BasePage.logFail()` and `BaseTest.assertTrue()`.

Generate and open the report:
```bash
mvn allure:serve
```

Allure results are written to `target/allure-results/` during the run.
