package com.base;

import com.capabilities.PCBrowserStackCapabilities;
import com.config.ConfigReader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.portal.pages.Pages;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

public class BaseTest {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();
    private static final ThreadLocal<Pages> COMMON_PAGES = new ThreadLocal<>();

    private static String OS;
    private static String OSVersion;
    private static String browserName;
    private static String browserVersion;
    private static String buildName;

    @BeforeSuite(alwaysRun = true)
    @Parameters({"OS", "OSVersion", "Device", "Browser", "BrowserVersion"})
    public void setUpSuite(String os, String osVersion, String device, String browser, String brwVersion) {
        OS = os;
        OSVersion = osVersion;
        browserName = browser;
        browserVersion = brwVersion;
        buildName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyyHHmm"));
    }

    @BeforeMethod(alwaysRun = true)
    public void launchBrowser(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        try {
            Playwright playwright = Playwright.create();
            PLAYWRIGHT.set(playwright);

            Browser browser;
            if ("browserstack".equalsIgnoreCase(ConfigReader.getRemoteProvider())) {
                if (OS == null || OS.isBlank()) {
                    throw new IllegalStateException("OS parameter is not set — check your TestNG suite XML.");
                }
                System.out.println("Connecting to BrowserStack via CDP for test: " + testName);
                browser = PCBrowserStackCapabilities.buildCdpWsUrl(
                        playwright, buildName, OS, OSVersion, browserName, browserVersion, testName);
                System.out.println("Connected to BrowserStack.");
            } else {
                BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                        .setHeadless(ConfigReader.isHeadless())
                        .setArgs(List.of(
                                "--disable-gpu",
                                "--no-sandbox",
                                "--disable-dev-shm-usage"
                        ));
                browser = playwright.chromium().launch(opts);
            }

            BROWSER.set(browser);
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setViewportSize(1920, 1080));
            CONTEXT.set(context);
            Page page = context.newPage();
            PAGE.set(page);
            result.setAttribute("PAGE_INSTANCE", page);

        } catch (Exception e) {
            System.err.println("Error launching browser for test '" + testName + "': " + e.getMessage());
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
    public void closeBrowser() {
        closeQuietly(PAGE, p -> { if (!p.isClosed()) p.close(); });
        closeQuietly(CONTEXT, BrowserContext::close);
        COMMON_PAGES.remove();
        closeQuietly(BROWSER, Browser::close);
        closeQuietly(PLAYWRIGHT, Playwright::close);
    }

    private <T> void closeQuietly(ThreadLocal<T> holder, Consumer<T> closer) {
        T resource = holder.get();
        if (resource != null) {
            try {
                closer.accept(resource);
            } catch (Exception e) {
                System.err.println("Warning closing resource: " + e.getMessage());
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
