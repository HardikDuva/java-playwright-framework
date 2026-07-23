package com.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PCBrowserStackCapabilities {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String BROWSERSTACK_CDP_URL_BASE = "wss://cdp.browserstack.com/playwright?caps=%s";

    private PCBrowserStackCapabilities() {}

    public static Browser buildCdpWsUrl(Playwright playwright,
                                        String buildName,
                                        String os,
                                        String osVersion,
                                        String browser,
                                        String browserVersion,
                                        String testName) {
        try {
            String username = System.getenv("BR_USERNAME");
            String accessKey = System.getenv("BR_ACCESS_KEY");

            if (username == null || username.isBlank()) {
                throw new IllegalStateException("Username is empty or null");
            }
            if (accessKey == null || accessKey.isBlank()) {
                throw new IllegalStateException("Access key is empty or null");
            }

            // Normalise to BrowserStack-accepted values: chrome, edge, playwright-webkit
            String normalizedBrowser = browser.trim().toLowerCase();

            // Build capabilities map
            Map<String, Object> bstackOptions = new HashMap<>();

            bstackOptions.put("browser_version", browserVersion);
            bstackOptions.put("os", os);
            bstackOptions.put("os_version", osVersion);
            bstackOptions.put("name", testName);
            bstackOptions.put("build", buildName);
            bstackOptions.put("project", "PlaywrightJavaDemo");
            bstackOptions.put("browserstack.username", username);
            bstackOptions.put("browserstack.accessKey", accessKey);

            // Route network traffic through a US IP for IP-level geo detection
            bstackOptions.put("browserstack.geoLocation", "US");

            Browser connectedBrowser;

            switch (normalizedBrowser) {
                case "chrome" -> {
                    bstackOptions.put("browser", "chrome");

                    // Auto-grant geolocation at the browser preference level so no prompt
                    // appears and Playwright's context.setGeolocation() can inject Las Vegas coords
                    Map<String, Object> prefs = new HashMap<>();
                    prefs.put("profile.default_content_setting_values.geolocation", 1);

                    Map<String, Object> chromeOptions = new HashMap<>();
                    chromeOptions.put("prefs", prefs);

                    bstackOptions.put("goog:chromeOptions", chromeOptions);

                    String wsUrl = String.format(BROWSERSTACK_CDP_URL_BASE,
                            URLEncoder.encode(OBJECT_MAPPER.writeValueAsString(bstackOptions), StandardCharsets.UTF_8));
                    connectedBrowser = playwright.chromium().connect(wsUrl);
                }
                case "edge", "microsoftedge" -> {
                    bstackOptions.put("browser", "edge");

                    Map<String, Object> prefs = new HashMap<>();
                    prefs.put("profile.default_content_setting_values.geolocation", 1);

                    Map<String, Object> edgeOptions = new HashMap<>();
                    edgeOptions.put("prefs", prefs);

                    bstackOptions.put("ms:edgeOptions", edgeOptions);

                    String wsUrl = String.format(BROWSERSTACK_CDP_URL_BASE,
                            URLEncoder.encode(OBJECT_MAPPER.writeValueAsString(bstackOptions), StandardCharsets.UTF_8));
                    connectedBrowser = playwright.chromium().connect(wsUrl);
                }

                case "safari" -> {
                    // BrowserStack requires "playwright-webkit" for Safari
                    // Geolocation permission is granted via context.setPermissions() in BaseTest
                    bstackOptions.put("browser", "playwright-webkit");

                    String wsUrl = String.format(BROWSERSTACK_CDP_URL_BASE,
                            URLEncoder.encode(OBJECT_MAPPER.writeValueAsString(bstackOptions), StandardCharsets.UTF_8));
                    connectedBrowser = playwright.webkit().connect(wsUrl);
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported browser: '" + browser + "'. Use chrome, edge, or safari.");
            }

            return connectedBrowser;

        } catch (IllegalStateException e) {
            System.err.println("[BrowserStack ERROR] " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("[BrowserStack ERROR] Failed to build CDP URL: " + e.getMessage());
            throw new RuntimeException(
                String.format(
                    "Failed to build BrowserStack CDP WebSocket URL for test '%s': %s",
                    testName,
                    e.getMessage()
                ),
                e
            );
        }
    }
}
