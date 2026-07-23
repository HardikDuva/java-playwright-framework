package com.base;

import java.util.regex.Pattern;

public final class StringValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Accepts: 1234567890, 123-456-7890, (123) 456-7890, +1 123-456-7890 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(\\+1[\\s-]?)?(\\(?\\d{3}\\)?[\\s.-]?)?\\d{3}[\\s.-]?\\d{4}$");

    /** Accepts: 12345 or 12345-6789 */
    private static final Pattern ZIP_PATTERN =
            Pattern.compile("^\\d{5}(-\\d{4})?$");

    private StringValidator() {}

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isValidZipCode(String zip) {
        return zip != null && ZIP_PATTERN.matcher(zip.trim()).matches();
    }
}
