package com.api;

import com.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;

import java.util.Map;

/**
 * Thin wrapper over Playwright's {@link APIRequestContext} for REST calls.
 *
 * Two uses:
 *  1. Direct API tests (see com.portal.tests.api).
 *  2. Fast test setup/teardown for UI tests — e.g. obtain an auth token or
 *     seed data via API instead of clicking through the UI.
 *
 * Owns its own Playwright instance so it can be used without a browser.
 * Always use in try-with-resources.
 */
public class ApiClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Playwright playwright;
    private final APIRequestContext request;

    public ApiClient() {
        this(ConfigReader.getApiBaseUrl());
    }

    public ApiClient(String baseUrl) {
        this.playwright = Playwright.create();
        this.request = playwright.request().newContext(
                new APIRequest.NewContextOptions()
                        .setBaseURL(baseUrl)
                        .setExtraHTTPHeaders(Map.of("Content-Type", "application/json")));
    }

    public APIRequestContext raw() {
        return request;
    }

    public APIResponse get(String path) {
        return request.get(path);
    }

    public APIResponse post(String path, Object jsonBody) {
        return request.post(path, RequestOptions.create().setData(jsonBody));
    }

    /**
     * Logs in via the REST API and returns the bearer token.
     * Endpoint/shape match practicesoftwaretesting.com's API.
     */
    public String loginAndGetToken(String email, String password) {
        APIResponse response = post("/users/login",
                Map.of("email", email, "password", password));
        if (!response.ok()) {
            throw new IllegalStateException(
                    "API login failed (" + response.status() + "): " + response.text());
        }
        return jsonField(response, "access_token");
    }

    public String jsonField(APIResponse response, String field) {
        try {
            JsonNode node = MAPPER.readTree(response.body());
            JsonNode value = node.get(field);
            return value != null ? value.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON field '" + field + "'", e);
        }
    }

    @Override
    public void close() {
        try {
            request.dispose();
        } finally {
            playwright.close();
        }
    }
}
