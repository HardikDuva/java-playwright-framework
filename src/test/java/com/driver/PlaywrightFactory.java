package com.driver;

import com.config.ConfigReader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Owns the expensive, reusable Playwright objects.
 *
 * A {@link Playwright} instance (which spawns a Node driver process) and a
 * {@link Browser} are created <b>once per thread</b> and reused across every
 * test that runs on that thread. Only the cheap {@code BrowserContext} is
 * created per test (in {@code BaseTest}), which is what actually isolates state.
 *
 * All created resources are also registered globally so {@link #disposeAll()}
 * can tear them down at the end of the suite regardless of which thread they
 * belong to.
 */
public final class PlaywrightFactory {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();

    // LIFO so browsers close before their owning Playwright instance.
    private static final Deque<AutoCloseable> RESOURCES = new ConcurrentLinkedDeque<>();

    private PlaywrightFactory() {}

    public static Playwright playwright() {
        Playwright pw = PLAYWRIGHT.get();
        if (pw == null) {
            pw = Playwright.create();
            PLAYWRIGHT.set(pw);
            RESOURCES.push(pw);
        }
        return pw;
    }

    /** Reusable local browser for the current thread (not used for BrowserStack). */
    public static Browser browser() {
        Browser b = BROWSER.get();
        if (b == null) {
            b = launchLocal(playwright());
            BROWSER.set(b);
            RESOURCES.push(b);
        }
        return b;
    }

    private static Browser launchLocal(Playwright pw) {
        boolean headless = ConfigReader.isHeadless();
        return switch (ConfigReader.getBrowserName()) {
            case "firefox" -> pw.firefox().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless));
            case "webkit"  -> pw.webkit().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless));
            default        -> pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            .setArgs(List.of(
                                    "--disable-gpu",
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage")));
        };
    }

    /** Closes every Playwright/Browser created during the run. Call once at suite end. */
    public static void disposeAll() {
        AutoCloseable r;
        while ((r = RESOURCES.poll()) != null) {
            try {
                r.close();
            } catch (Exception ignored) {
                // Best effort — suite is ending.
            }
        }
        BROWSER.remove();
        PLAYWRIGHT.remove();
    }
}
