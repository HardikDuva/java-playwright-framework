package com.config;

import com.enums.UserRole;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigReader {

    public static final String CONFIG_FILE = "config/config.properties";

    private static final int DEFAULT_VISIBILITY_TIMEOUT_MS  = 10000;
    private static final int DEFAULT_NAVIGATION_TIMEOUT_MS  = 10000;

    private static final Properties properties = new Properties();

    // FIXED: Thread-safe cache using ConcurrentHashMap
    private static final Map<String, String> propertyCache = new ConcurrentHashMap<>();

    static {
        try (InputStream input =
                     ConfigReader.class
                             .getClassLoader()
                             .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new RuntimeException(
                        "Config file not found in classpath: " + CONFIG_FILE +
                                ". Ensure config.properties is in src/test/resources/config/");
            }

            properties.load(input);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load config file: " + CONFIG_FILE, e);
        }
    }

    // OPTIMIZATION: Cache property lookups to avoid repeated Map lookups
    private static String getProperty(String key) {
        // Check cache first
        String cached = propertyCache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        // Get from properties and cache (including null values as empty string)
        String value = properties.getProperty(key);
        propertyCache.put(key, value != null ? value : "");
        return value;
    }

    private static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int getVisibilityTimeout() {
        return getIntProperty("visibility.wait.timeout", DEFAULT_VISIBILITY_TIMEOUT_MS);
    }

    public static int getNavigationTimeout() {
        return getIntProperty("navigation.wait.timeout", DEFAULT_NAVIGATION_TIMEOUT_MS);
    }

    public static String getRemoteProvider() {
        return getProperty("REMOTE_PROVIDER");
    }

    public static String getBaseUrl() {
        return getProperty("BASE_URL");
    }

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

    public static boolean isHeadless() {
        return Boolean.parseBoolean(getProperty("browser.headless"));
    }
}
