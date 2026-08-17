package com.config;

import com.enums.UserRole;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads configuration with a clear precedence, highest wins:
 *
 *   1. JVM system property        -Dadmin.login.password=...
 *   2. OS environment variable    ADMIN_LOGIN_PASSWORD   (key upper-cased, '.'->'_')
 *   3. Environment overlay file   config/config-<env>.properties   (-Denv=staging)
 *   4. Base file                  config/config.properties
 *   5. Hard-coded default
 *
 * Secrets should be supplied via (1) or (2) in CI so they never live in git.
 */
public class ConfigReader {

    public static final String CONFIG_FILE = "config/config.properties";

    private static final int DEFAULT_VISIBILITY_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_NAVIGATION_TIMEOUT_MS  = 30_000;

    private static final Properties properties = new Properties();

    // Cache of resolved lookups. Empty string is the sentinel for "resolved to null".
    private static final Map<String, String> propertyCache = new ConcurrentHashMap<>();

    static {
        load(CONFIG_FILE);
        // Optional per-environment overlay, e.g. -Denv=staging -> config/config-staging.properties
        String env = System.getProperty("env", System.getenv("ENV"));
        if (env != null && !env.isBlank()) {
            load("config/config-" + env.trim() + ".properties");
        }
    }

    private static void load(String resource) {
        try (InputStream input =
                     ConfigReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                if (CONFIG_FILE.equals(resource)) {
                    throw new RuntimeException(
                            "Config file not found in classpath: " + resource +
                                    ". Ensure config.properties is in src/test/resources/config/");
                }
                // Overlay files are optional.
                return;
            }
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config file: " + resource, e);
        }
    }

    private static String getProperty(String key) {
        String cached = propertyCache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String value = resolve(key);
        propertyCache.put(key, value != null ? value : "");
        return value;
    }

    private static String resolve(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env;
        }
        return properties.getProperty(key);
    }

    private static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Integer.parseInt(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBoolProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    // --- Timeouts -----------------------------------------------------------

    public static int getVisibilityTimeout() {
        return getIntProperty("visibility.wait.timeout", DEFAULT_VISIBILITY_TIMEOUT_MS);
    }

    public static int getNavigationTimeout() {
        return getIntProperty("navigation.wait.timeout", DEFAULT_NAVIGATION_TIMEOUT_MS);
    }

    // --- Environment / targets ---------------------------------------------

    public static String getRemoteProvider() {
        String p = getProperty("REMOTE_PROVIDER");
        return p != null ? p : "local";
    }

    public static String getBaseUrl() {
        return getProperty("BASE_URL");
    }

    public static String getApiBaseUrl() {
        String p = getProperty("API_BASE_URL");
        return p != null ? p : "https://api.practicesoftwaretesting.com";
    }

    // --- Browser ------------------------------------------------------------

    public static boolean isHeadless() {
        return getBoolProperty("browser.headless", true);
    }

    /** Local browser engine: chromium (default), firefox, or webkit. */
    public static String getBrowserName() {
        String raw = getProperty("browser.name");
        if (raw == null || raw.isBlank()) {
            return "chromium";
        }
        return switch (raw.trim().toLowerCase()) {
            case "firefox", "ff"                 -> "firefox";
            case "webkit", "safari"              -> "webkit";
            case "chrome", "edge", "msedge",
                 "chromium"                      -> "chromium";
            default                              -> "chromium";
        };
    }

    // --- Diagnostics --------------------------------------------------------

    /** Playwright trace captured per test and saved on failure. Default on. */
    public static boolean isTraceEnabled() {
        return getBoolProperty("trace.enabled", true);
    }

    /** Record video per test, kept only on failure. Default off (overhead). */
    public static boolean isVideoOnFailure() {
        return getBoolProperty("video.on.failure", false);
    }

    // --- Credentials (prefer env/system-prop over file) ---------------------

    public static String getUsername(UserRole role) {
        return switch (role) {
            case ADMIN -> getProperty("admin.login.email");
            case USER  -> getProperty("user.login.email");
        };
    }

    public static String getPassword(UserRole role) {
        return switch (role) {
            case ADMIN -> getProperty("admin.login.password");
            case USER  -> getProperty("user.login.password");
        };
    }
}
