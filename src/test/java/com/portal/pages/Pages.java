package com.portal.pages;

import com.microsoft.playwright.Page;
import com.portal.pages.admin.DashboardPage;
import com.portal.pages.user.UserHomePage;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Pages {

    private final ConcurrentMap<Class<?>, Object> pageCache = new ConcurrentHashMap<>();
    private final Page page;

    public Pages(Page page) {
        this.page = Objects.requireNonNull(page, "page must not be null");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        return (T) pageCache.computeIfAbsent(type, this::instantiate);
    }

    private Object instantiate(Class<?> cls) {
        try {
            Constructor<?> ctor = cls.getConstructor(Page.class);
            return ctor.newInstance(page);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No constructor(Page) found on " + cls.getName(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + cls.getName(), e);
        }
    }

    public LoginPage loginPage() {
        return get(LoginPage.class);
    }

    public DashboardPage dashboardPage() {
        return get(DashboardPage.class);
    }

    public UserHomePage homePage() {
        return get(UserHomePage.class);
    }

}
