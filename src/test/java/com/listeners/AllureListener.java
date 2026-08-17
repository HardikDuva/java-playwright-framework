package com.listeners;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class AllureListener {

    private static final Logger log = LoggerFactory.getLogger(AllureListener.class);

    public static void addTestStep(String stepDescription) {
        Allure.step(stepDescription);
    }

    public static void captureAndAttachScreenshot(Page page) {
        if (page == null || page.isClosed()) {
            log.warn("Cannot capture screenshot: page is null or closed");
            return;
        }
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Screenshot", "image/png",
                    new ByteArrayInputStream(screenshot), ".png");
        } catch (Exception e) {
            log.warn("Failed to capture screenshot: {}", e.getMessage());
        }
    }

    /** Attaches a file on disk (trace zip, video, etc.) to the Allure report. */
    public static void attachFile(String name, Path file, String mimeType) {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String ext = "." + fileExtension(file);
            Allure.addAttachment(name, mimeType, Files.newInputStream(file), ext);
        } catch (Exception e) {
            log.warn("Failed to attach file '{}': {}", file, e.getMessage());
        }
    }

    private static String fileExtension(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot >= 0 ? fn.substring(dot + 1) : "bin";
    }
}
