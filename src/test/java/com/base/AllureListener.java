package com.base;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

public class AllureListener {

    public static void addTestStep(String stepDescription) {
        Allure.step(stepDescription);
    }

    public static void captureAndAttachScreenshot(Page page) {
        if (page == null || page.isClosed()) {
            System.err.println("Cannot capture screenshot: page is null or closed");
            return;
        }
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Screenshot", "image/png", new ByteArrayInputStream(screenshot), ".png");
        } catch (Exception e) {
            System.err.println("Failed to capture screenshot: " + e.getMessage());
        }
    }
}
