package com.retail.notification.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** A strict check: no spaces, commas, quotes or angle brackets, so an address can never change the mail headers. */
final class Recipients {
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$");

    private Recipients() {}

    static boolean isValid(String email) {
        return email != null && email.length() <= 255 && EMAIL.matcher(email).matches();
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}