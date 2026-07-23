package com.portal.pages;

import com.base.BasePage;
import com.config.ConfigReader;
import com.enums.UserRole;
import com.microsoft.playwright.Page;

public class LoginPage extends BasePage {

    private static final String EMAIL_INPUT         = "[data-test='email']";
    private static final String PASSWORD_INPUT      = "[data-test='password']";
    private static final String SIGN_IN_BUTTON      = "[data-test='login-submit']";
    private static final String FORGOT_PASSWORD_LINK = "[data-test='forgot-password-link']";
    private static final String EMAIL_ERROR         = "[data-test='email-error']";
    private static final String PASSWORD_ERROR      = "[data-test='password-error']";

    public LoginPage(Page page) {
        super(page);
    }

    public void navigateToLogin() {
        navigate(ConfigReader.getBaseUrl() + "/auth/login");
    }

    public void loginWithRole(UserRole role) {
        logInfo("Logging in as: " + role);
        fill(EMAIL_INPUT, ConfigReader.getUsername(role));
        fill(PASSWORD_INPUT, ConfigReader.getPassword(role));
        click(SIGN_IN_BUTTON);
    }

    public void loginWithCredentials(String email, String password) {
        logInfo("Logging in with email: " + email);
        page.locator(EMAIL_INPUT).fill(email);
        page.locator(PASSWORD_INPUT).fill(password);
        click(SIGN_IN_BUTTON);
    }

    public void clickForgotPassword() {
        click(FORGOT_PASSWORD_LINK);
    }

    public boolean isLoginFormVisible() {
        return isLocatorVisible(EMAIL_INPUT)
                && isLocatorVisible(PASSWORD_INPUT)
                && isLocatorVisible(SIGN_IN_BUTTON);
    }

    public boolean isEmailErrorVisible() {
        return isLocatorVisible(EMAIL_ERROR);
    }

    public boolean isPasswordErrorVisible() {
        return isLocatorVisible(PASSWORD_ERROR);
    }
}
