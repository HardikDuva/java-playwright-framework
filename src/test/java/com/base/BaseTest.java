package com.base;

import com.capabilities.PCBrowserStackCapabilities;
import com.config.ConfigReader;
import com.driver.PlaywrightFactory;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.portal.pages.Pages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    // Per-test, isolated objects. Playwright + local Browser are reused per thread
    // via PlaywrightFactory; only the context/page are recreated per test.
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    private static final ThreadLocal<Pages> COMMON_PAGES = new ThreadLocal<>();
    // Only set when running on BrowserStack (one remote session per test).
    private static final ThreadLocal<Browser> BS_BROWSER = new ThreadLocal<>();

    private static final Path TRACE_DIR = Paths.get("target", "traces");
    private static final Path VIDEO_DIR = Paths.get("target", "videos");

    private static volatile String OS;
    private static volatile String OSVersion;
    private static volatile String browserName;
    private static volatile String browserVersion;
    private static volatile String buildName;

    @BeforeSuite(alwaysRun = true)
    @Parameters({"OS", "OSVersion", "Device", "Browser", "BrowserVersion"})
    public void setUpSuite(@Optional String os,
                           @Optional String osVersion,
                           @Optional String device,
                           @Optional String browser,
                           @Optional String brwVersion) {
        OS = os;
        OSVersion = osVersion;
        browserName = browser;
        browserVersion = brwVersion;
        buildName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyyHHmm"));
    }

    @BeforeMethod(alwaysRun = true)
    public void launchBrowser(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        MDC.put("test", testName);

        try {
            Browser browser;
            if ("browserstack".equalsIgnoreCase(ConfigReader.getRemoteProvider())) {
                if (OS == null || OS.isBlank()) {
                    throw new IllegalStateException("OS parameter is not set — check your TestNG suite XML.");
                }
                browser = PCBrowserStackCapabilities.buildCdpWsUrl(
                        PlaywrightFactory.playwright(), buildName, OS, OSVersion,
                        browserName, browserVersion, testName);
                BS_BROWSER.set(browser);
            } else {
                browser = PlaywrightFactory.browser();
            }

            Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080);
            if (ConfigReader.isVideoOnFailure()) {
                ctxOpts.setRecordVideoDir(VIDEO_DIR);
            }

            BrowserContext context = browser.newContext(ctxOpts);
            CONTEXT.set(context);

            if (ConfigReader.isTraceEnabled()) {
                context.tracing().start(new Tracing.StartOptions()
                        .setScreenshots(true)
                        .setSnapshots(true)
                        .setSources(true)
                        .setTitle(testName));
            }

            Page page = context.newPage();
            PAGE.set(page);
            result.setAttribute("PAGE_INSTANCE", page);

        } catch (Exception e) {
            log.error("Error launching browser for test '{}': {}", testName, e.getMessage());
            throw new RuntimeException("Failed to launch browser", e);
        }
    }

    protected Page getPage() {
        Page page = PAGE.get();
        if (page == null) {
            throw new IllegalStateException("Page not initialized. Ensure @BeforeMethod ran first.");
        }
        return page;
    }

    protected Pages commonPages() {
        Pages p = COMMON_PAGES.get();
        if (p == null) {
            p = new Pages(getPage());
            COMMON_PAGES.set(p);
        }
        return p;
    }

    @AfterMethod(alwaysRun = true)
    public void closeBrowser(ITestResult result) {
        boolean failed = result.getStatus() == ITestResult.FAILURE;
        String testName = result.getMethod().getMethodName();
        Page page = PAGE.get();
        BrowserContext context = CONTEXT.get();

        // Screenshot before we tear anything down.
        if (failed && page != null && !page.isClosed()) {
            AllureListener.captureAndAttachScreenshot(page);
        }

        // Stop tracing: persist + attach only on failure, discard otherwise.
        if (context != null && ConfigReader.isTraceEnabled()) {
            try {
                if (failed) {
                    Path trace = TRACE_DIR.resolve(sanitize(testName) + "-"
                            + Thread.currentThread().getId() + ".zip");
                    Files.createDirectories(TRACE_DIR);
                    context.tracing().stop(new Tracing.StopOptions().setPath(trace));
                    AllureListener.attachFile(
                            "Playwright Trace (open with: npx playwright show-trace <file>)",
                            trace, "application/zip");
                } else {
                    context.tracing().stop();
                }
            } catch (Exception e) {
                log.warn("Failed to stop tracing for '{}': {}", testName, e.getMessage());
            }
        }

        // Keep a video handle before the context closes (path resolves post-close).
        com.microsoft.playwright.Video video =
                (failed && page != null) ? safeVideo(page) : null;

        closeQuietly(PAGE, p -> { if (!p.isClosed()) p.close(); });
        closeQuietly(CONTEXT, BrowserContext::close);
        COMMON_PAGES.remove();

        if (video != null) {
            try {
                AllureListener.attachFile("Video", video.path(), "video/webm");
            } catch (Exception e) {
                log.warn("Failed to attach video for '{}': {}", testName, e.getMessage());
            }
        }

        // BrowserStack sessions are per-test; local browsers are reused per thread.
        closeQuietly(BS_BROWSER, Browser::close);

        MDC.remove("test");
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        PlaywrightFactory.disposeAll();
    }

    private com.microsoft.playwright.Video safeVideo(Page page) {
        try {
            return page.video();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private <T> void closeQuietly(ThreadLocal<T> holder, Consumer<T> closer) {
        T resource = holder.get();
        if (resource != null) {
            try {
                closer.accept(resource);
            } catch (Exception e) {
                log.warn("Warning closing resource: {}", e.getMessage());
            } finally {
                holder.remove();
            }
        }
    }

    protected void assertTrue(boolean condition, String message) {
        if (!condition) {
            AllureListener.captureAndAttachScreenshot(getPage());
            Assert.fail(message);
        }
    }
}
