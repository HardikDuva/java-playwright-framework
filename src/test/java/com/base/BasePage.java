package com.base;

import com.config.ConfigReader;
import com.listeners.AllureListener;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class BasePage {

    protected final Page page;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final int NAVIGATION_TIMEOUT_MS = ConfigReader.getNavigationTimeout();
    private static final int VISIBILITY_TIMEOUT_MS = ConfigReader.getVisibilityTimeout();

    private static final Page.NavigateOptions NAVIGATE_OPTIONS =
            new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.LOAD)
                    .setTimeout(NAVIGATION_TIMEOUT_MS);

    private static final Page.WaitForSelectorOptions VISIBLE_SELECTOR_OPTIONS =
            new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(VISIBILITY_TIMEOUT_MS);

    private static final String FILE_DATA_DIR = System.getProperty("user.dir") + "/FileData/";

    public BasePage(Page page) {
        this.page = page;
        if (page != null && !page.isClosed()) {
            page.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT_MS);
            page.setDefaultTimeout(VISIBILITY_TIMEOUT_MS);
        }
    }

    // --- Logging ------------------------------------------------------------

    public void logInfo(String message) {
        AllureListener.addTestStep(message);
        log.info(message);
    }

    public void logFail(String message, String... extra) {
        String fullMessage = extra.length > 0 ? message + extra[0] : message;
        AllureListener.addTestStep(fullMessage);
        log.error(fullMessage);
        AllureListener.captureAndAttachScreenshot(this.page);
        Assert.fail(fullMessage);
    }

    // --- Locator helpers ----------------------------------------------------

    protected Locator locator(String selector) {
        return page.locator(selector);
    }

    // --- Actions ------------------------------------------------------------

    public void navigate(String url) {
        page.navigate(url, NAVIGATE_OPTIONS);
    }

    public void click(String selector) {
        if (isPageUnusable()) {
            logFail("Click failed — page is null or closed. Selector: '" + selector + "'");
            return;
        }
        try {
            // Playwright auto-waits for the element to be visible, enabled and stable.
            page.locator(selector).click();
        } catch (PlaywrightException e) {
            logFail("Click failed on selector '" + selector + "'", " — " + e.getMessage());
        }
    }

    public void fill(String selector, String value) {
        if (isPageUnusable()) {
            logFail("Fill failed — page is null or closed. Selector: '" + selector + "'");
            return;
        }
        if (selector == null || selector.isEmpty() || value == null) {
            logFail("Fill failed — selector or value is null/empty");
            return;
        }
        try {
            page.locator(selector).fill(value);
            logInfo("Filled '" + selector + "' with value '" + value + "'");
        } catch (Exception e) {
            logFail("Fill failed on selector '" + selector + "'", ": " + e.getMessage());
        }
    }

    public String getText(String selector) {
        if (isPageUnusable()) {
            logFail("getText failed — page is null or closed. Selector: '" + selector + "'");
            return null;
        }
        try {
            return page.locator(selector).innerText();
        } catch (Exception e) {
            logFail("getText failed on selector '" + selector + "'", ": " + e.getMessage());
            return null;
        }
    }

    // --- Waits & assertions -------------------------------------------------

    public void waitUntilLocatorVisible(String selector) {
        if (isPageUnusable()) {
            logFail("waitUntilLocatorVisible failed — page is null or closed. Selector: '" + selector + "'");
            return;
        }
        try {
            page.waitForSelector(selector, VISIBLE_SELECTOR_OPTIONS);
            logInfo("Element visible: " + selector);
        } catch (Exception e) {
            logFail("Timed out waiting for selector '" + selector + "'", ": " + e.getMessage());
        }
    }

    /**
     * Web-first assertion: retries until the element is visible or the timeout
     * elapses, then fails the test with a rich message. Prefer this over
     * {@link #isLocatorVisible} when visibility is the thing under test.
     */
    public void assertVisible(String selector) {
        assertThat(page.locator(selector)).isVisible();
    }

    /**
     * Non-throwing visibility probe. Returns false if the element does not
     * become visible within the configured timeout. Use for branching, not
     * for the primary assertion of a test.
     */
    public boolean isLocatorVisible(String selector) {
        if (isPageUnusable()) return false;
        try {
            page.waitForSelector(selector, VISIBLE_SELECTOR_OPTIONS);
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    public void waitForAllLoadersToDisappear(String selector) {
        int timeoutMs = 20_000;
        try {
            try {
                page.waitForFunction(
                        "sel => {" +
                        "  const els = document.querySelectorAll(sel);" +
                        "  if (els.length === 0) return false;" +
                        "  return Array.from(els).some(el => {" +
                        "    const s = window.getComputedStyle(el);" +
                        "    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0;" +
                        "  });" +
                        "}",
                        selector,
                        new Page.WaitForFunctionOptions().setTimeout(5_000)
                );
                logInfo("Loader appeared — waiting for it to disappear: " + selector);
            } catch (TimeoutError ignored) {
                logInfo("Loader did not appear within 5s — action may have completed instantly");
                return;
            }

            page.waitForFunction(
                    "sel => {" +
                    "  const els = document.querySelectorAll(sel);" +
                    "  if (els.length === 0) return true;" +
                    "  return Array.from(els).every(el => {" +
                    "    const s = window.getComputedStyle(el);" +
                    "    return s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) === 0;" +
                    "  });" +
                    "}",
                    selector,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs)
            );
            logInfo("Loader disappeared: " + selector);

        } catch (TimeoutError e) {
            logFail("Loader '" + selector + "' still visible after " + timeoutMs + "ms.");
        }
    }

    public void waitForURLToLoad(String urlPattern) {
        if (isPageUnusable()) {
            logFail("waitForURLToLoad failed — page is null or closed. Pattern: '" + urlPattern + "'");
            return;
        }
        try {
            page.waitForURL(Pattern.compile(urlPattern));
            logInfo("URL matched pattern: " + urlPattern);
        } catch (Exception e) {
            logFail("Timed out waiting for URL pattern '" + urlPattern + "'", ": " + e.getMessage());
        }
    }

    public void uploadFile(String selector, String fileName) {
        if (isPageUnusable()) {
            logFail("uploadFile failed — page is null or closed. File: '" + fileName + "'");
            return;
        }
        try {
            Locator fileInput = page.locator("input[type='file']");
            fileInput.setInputFiles(Paths.get(FILE_DATA_DIR + fileName));
        } catch (Exception e) {
            logFail("File upload failed for '" + fileName + "' on selector '" + selector + "'", ": " + e.getMessage());
        }
    }

    private boolean isPageUnusable() {
        return page == null || page.isClosed();
    }
}
