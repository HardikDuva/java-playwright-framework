package com.portal.pages.admin;

import com.base.BasePage;
import com.microsoft.playwright.Page;

public class DashboardPage extends BasePage {

    private static final String NAVBAR_BRAND = "//a[@class='navbar-brand']";

    public DashboardPage(Page page) {
        super(page);
    }

    public void waitForAdminPortal() {
        logInfo("Waiting for admin dashboard");
        waitUntilLocatorVisible(NAVBAR_BRAND);
        waitForURLToLoad("/admin/dashboard");
        logInfo("Admin dashboard loaded");
    }
}
