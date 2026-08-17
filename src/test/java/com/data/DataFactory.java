package com.data;

import net.datafaker.Faker;

import java.util.Locale;
import java.util.UUID;

/**
 * Generates realistic, unique test data so parallel tests never share mutable
 * state. Datafaker is deterministic-friendly and requires no network/LLM at
 * runtime. For exotic edge-case datasets, generate them offline and check them
 * in as fixtures rather than calling an LLM inside a test.
 */
public final class DataFactory {

    private static final Faker FAKER = new Faker(Locale.of("en", "US"));

    private DataFactory() {}

    /** A fresh user whose email is guaranteed unique across parallel threads. */
    public static TestUser newUser() {
        String first = FAKER.name().firstName();
        String last = FAKER.name().lastName();
        return new TestUser(
                first,
                last,
                uniqueEmail(first, last),
                strongPassword(),
                FAKER.phoneNumber().cellPhone(),
                FAKER.address().streetAddress(),
                FAKER.address().city(),
                FAKER.address().stateAbbr(),
                FAKER.address().postcode(),
                FAKER.address().country());
    }

    public static String uniqueEmail(String first, String last) {
        String slug = UUID.randomUUID().toString().substring(0, 8);
        return (first + "." + last + "." + slug + "@example.com")
                .toLowerCase(Locale.ROOT);
    }

    public static String uniqueEmail() {
        return "user." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** Meets common complexity rules: upper, lower, digit, symbol, length >= 10. */
    public static String strongPassword() {
        return "Aa1!" + FAKER.internet().password(6, 10, true, true, true);
    }
}
