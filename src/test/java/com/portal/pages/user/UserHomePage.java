package com.portal.pages.user;

import com.base.BasePage;
import com.microsoft.playwright.Page;

public class UserHomePage extends BasePage {

    private static final String NAVBAR_BRAND = "//a[@class='navbar-brand']";

    public UserHomePage(Page page) {
        super(page);
    }

    public void waitForHomePage() {
        logInfo("Waiting for user home page");
        waitUntilLocatorVisible(NAVBAR_BRAND);
        waitForURLToLoad("/account");
        logInfo("User home page loaded");
    }
}
