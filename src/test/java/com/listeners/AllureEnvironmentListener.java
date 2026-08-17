package com.listeners;

import com.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Enriches the Allure report at the end of the run:
 *  - writes {@code environment.properties} (shown in the report's Environment widget)
 *  - copies {@code categories.json} so failures are classified (product defect,
 *    test defect, infrastructure, flaky, ...).
 *
 * Auto-registered via META-INF/services/org.testng.ITestNGListener.
 */
public class AllureEnvironmentListener implements ISuiteListener {

    private static final Logger log = LoggerFactory.getLogger(AllureEnvironmentListener.class);

    @Override
    public void onFinish(ISuite suite) {
        Path resultsDir = Paths.get(
                System.getProperty("allure.results.directory", "target/allure-results"));
        try {
            Files.createDirectories(resultsDir);
            writeEnvironment(resultsDir);
            copyCategories(resultsDir);
        } catch (Exception e) {
            log.warn("Could not write Allure environment metadata: {}", e.getMessage());
        }
    }

    private void writeEnvironment(Path resultsDir) throws Exception {
        Properties env = new Properties();
        env.setProperty("Base.URL", str(ConfigReader.getBaseUrl()));
        env.setProperty("API.Base.URL", str(ConfigReader.getApiBaseUrl()));
        env.setProperty("Remote.Provider", str(ConfigReader.getRemoteProvider()));
        env.setProperty("Browser", str(ConfigReader.getBrowserName()));
        env.setProperty("Headless", String.valueOf(ConfigReader.isHeadless()));
        env.setProperty("Environment", System.getProperty("env",
                System.getenv().getOrDefault("ENV", "default")));
        env.setProperty("Java.Version", System.getProperty("java.version"));
        env.setProperty("OS", System.getProperty("os.name"));

        try (var out = Files.newOutputStream(resultsDir.resolve("environment.properties"))) {
            env.store(out, "Allure environment");
        }
    }

    private void copyCategories(Path resultsDir) throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("allure/categories.json")) {
            if (in != null) {
                Files.copy(in, resultsDir.resolve("categories.json"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String str(String v) {
        return v != null ? v : "";
    }
}
