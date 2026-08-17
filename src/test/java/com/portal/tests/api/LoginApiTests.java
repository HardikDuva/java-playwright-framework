package com.portal.tests.api;

import com.api.ApiClient;
import com.config.ConfigReader;
import com.enums.UserRole;
import com.microsoft.playwright.APIResponse;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Example API-level tests. These run without a browser and are far faster than
 * the UI equivalents — the same auth flow can be reused to set up UI tests.
 *
 * Part of the "api" group; not included in the default UI suite.
 */
@Epic("Authentication")
@Feature("Login API")
public class LoginApiTests {

    @Test(groups = {"api", "regression"})
    public void validCredentialsReturnToken() {
        try (ApiClient api = new ApiClient()) {
            String token = api.loginAndGetToken(
                    ConfigReader.getUsername(UserRole.ADMIN),
                    ConfigReader.getPassword(UserRole.ADMIN));
            Assert.assertNotNull(token, "Login API must return an access token");
            Assert.assertFalse(token.isBlank(), "Access token must not be blank");
        }
    }

    @Test(groups = {"api", "regression"})
    public void invalidCredentialsAreRejected() {
        try (ApiClient api = new ApiClient()) {
            APIResponse response = api.post("/users/login",
                    Map.of("email", "no.such.user@example.com", "password", "WrongPass@999"));
            Assert.assertFalse(response.ok(),
                    "Login API must reject invalid credentials (got HTTP " + response.status() + ")");
        }
    }
}
