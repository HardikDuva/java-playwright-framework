package com.portal.tests.functional;

import com.base.BaseTest;
import com.config.ConfigReader;
import com.enums.UserRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

@Epic("Authentication")
@Feature("Login")
public class LoginTests extends BaseTest {

    private static final String ADMIN_EMAIL    = ConfigReader.getUsername(UserRole.ADMIN);
    private static final String WRONG_PASSWORD = "WrongPass@999";
    private static final String INVALID_EMAIL  = "notanemail";

   @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.BLOCKER)
    public void adminCanLoginSuccessfully() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithRole(UserRole.ADMIN);
        commonPages().dashboardPage().waitForAdminPortal();
    }

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.BLOCKER)
    public void userCanLoginSuccessfully() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithRole(UserRole.USER);
        commonPages().homePage().waitForHomePage();
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void forgotPasswordLinkNavigatesAwayFromLogin() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().clickForgotPassword();
        assertTrue(
                getPage().url().contains("/auth/forgot-password"),
                "URL must navigate to forgot password page after clicking the link"
        );
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void emptyFormSubmitShowsBothFieldErrors() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithCredentials("", "");
        assertTrue(
                commonPages().loginPage().isEmailErrorVisible(),
                "Email required error must appear after submitting empty form"
        );
        assertTrue(
                commonPages().loginPage().isPasswordErrorVisible(),
                "Password required error must appear after submitting empty form"
        );
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void emptyEmailShowsEmailRequiredError() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithCredentials("", WRONG_PASSWORD);
        assertTrue(
                commonPages().loginPage().isEmailErrorVisible(),
                "Email required error must appear when email field is empty"
        );
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void emptyPasswordShowsPasswordRequiredError() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithCredentials(ADMIN_EMAIL, "");
        assertTrue(
                commonPages().loginPage().isPasswordErrorVisible(),
                "Password required error must appear when password field is empty"
        );
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void invalidEmailFormatShowsEmailError() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithCredentials(INVALID_EMAIL, WRONG_PASSWORD);
        assertTrue(
                commonPages().loginPage().isEmailErrorVisible(),
                "Email validation error must appear for an invalid email format"
        );
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    public void incorrectCredentialsStaysOnLoginPage() {
        commonPages().loginPage().navigateToLogin();
        commonPages().loginPage().loginWithCredentials("no.such.user@example.com", WRONG_PASSWORD);
        assertTrue(
                getPage().url().contains("/auth/login"),
                "URL must remain on login page after incorrect credentials"
        );
    }
}
