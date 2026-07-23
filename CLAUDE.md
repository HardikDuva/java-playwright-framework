# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Demo end-to-end test automation framework showing **Playwright + Java + TestNG** parallel execution. Tests run against [practicesoftwaretesting.com](https://practicesoftwaretesting.com).

---

## Commands

### Run the test suite
```bash
mvn clean test
```

### Run a specific suite file
```bash
mvn clean test -DsuiteXmlFile=src/test/resources/TestSuite/LoginTests.xml
```

### Compile only (no tests)
```bash
mvn clean compile test-compile
```

### Install Playwright browsers (first-time setup)
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium" -D exec.classpathScope=test
```

### BrowserStack (optional remote execution)
```bash
export BR_USERNAME=<browserstack-username>
export BR_ACCESS_KEY=<browserstack-access-key>
# Then set REMOTE_PROVIDER=browserstack in config.properties
```

---

## Architecture

```
TestNG XML Suite (LoginTests.xml)
        │  parallel="methods", thread-count="4"
        ▼
BaseTest  (@BeforeSuite / @BeforeMethod / @AfterMethod)
        │  ThreadLocal<Playwright, Browser, BrowserContext, Page> per test method
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
AllureListener  (screenshot on failure → attached to Allure report)
```

**No Steps layer.** Tests call page objects directly.

---

## Key Classes

| Class | Package | Role |
|---|---|---|
| `BaseTest` | `com.base` | Browser lifecycle (`@BeforeMethod` / `@AfterMethod`), `getPage()`, `commonPages()`, `assertTrue()` |
| `BasePage` | `com.base` | Playwright `Page` wrapper — `click`, `fill`, `getText`, `waitUntilLocatorVisible`, `isLocatorVisible`, `waitForAllLoadersToDisappear`, `waitForURLToLoad`, `uploadFile` |
| `AllureListener` | `com.base` | Static `addTestStep(String)` and `captureAndAttachScreenshot(Page)` — called from `BasePage.logFail` and `BaseTest.assertTrue` |
| `StringValidator` | `com.base` | Static helpers: `isValidEmail`, `isValidPhone`, `isValidZipCode` |
| `Pages` | `com.portal.pages` | Aggregator with typed accessors; uses `ConcurrentHashMap` + reflection for lazy instantiation |
| `LoginPage` | `com.portal.pages` | Login form: `navigateToLogin`, `loginWithRole(UserRole)`, `loginWithCredentials`, `isLoginFormVisible`, `isEmailErrorVisible`, `isPasswordErrorVisible` |
| `DashboardPage` | `com.portal.pages.admin` | `waitForAdminPortal()` — waits for navbar and `/admin/dashboard` URL |
| `UserHomePage` | `com.portal.pages.user` | `waitForHomePage()` — waits for navbar and `/account` URL |
| `ConfigReader` | `com.config` | Loads `config/config.properties` from classpath; thread-safe property cache |
| `UserRole` | `com.enums` | `ADMIN`, `USER` |
| `PCBrowserStackCapabilities` | `com.capabilities` | Builds BrowserStack CDP WebSocket URL; supports Chrome, Edge, Safari |

---

## Configuration

**`src/test/resources/config/config.properties`**

- `BASE_URL` — target site (default: `https://practicesoftwaretesting.com`)
- `REMOTE_PROVIDER` — `local` or `browserstack`
- `browser.headless` — `true` / `false`
- `admin.login.email` / `admin.login.password`
- `user.login.email` / `user.login.password`
- `visibility.wait.timeout` — ms, default `10000`
- `navigation.wait.timeout` — ms, default `30000`

---

## Writing New Tests

1. Create a test class in `com.portal.tests`, extend `BaseTest`.
2. Access pages via `commonPages().<page>().<action>()`.
3. Assert with `assertTrue(condition, message)` — captures a screenshot on failure.
4. Register the class in `src/test/resources/TestSuite/LoginTests.xml`.

### Adding a new page object

- Create the class in `com.portal.pages.*`, extend `BasePage`.
- Provide a `constructor(Page page)` — `Pages` uses reflection for instantiation.
- Add a typed accessor method to `com.portal.pages.Pages`.

---

## TestNG Suite XML

`LoginTests.xml` — `parallel="methods"`, `thread-count="4"`.

Suite parameters passed to `BaseTest.setUpSuite()`: `OS`, `OSVersion`, `Device`, `Browser`, `BrowserVersion`.

- `OS` and `OSVersion` — used by `PCBrowserStackCapabilities` when `REMOTE_PROVIDER=browserstack` to set the desktop OS capability.
- `Browser` and `BrowserVersion` — used by `PCBrowserStackCapabilities` to select the browser.
- `Device` — present in the XML but not consumed anywhere; unused.

---

## Commit Conventions

- **Format:** `<type>(<scope>): <description>` — types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`
- **Max 50 chars**, imperative mood, no trailing period
- **Branch format:** `<type>/<short-description>` — e.g., `feat/add-product-page-tests`
