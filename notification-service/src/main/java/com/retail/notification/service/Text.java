package com.retail.notification.service;

/** Cleans text that goes into an email. Customer-typed text must never add lines to a mail header. */
final class Text {
    private Text() {}

    /** One line: every control character (line breaks included) becomes a space, runs of spaces collapse, then it is cut to max. */
    static String line(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max).trim() : cleaned;
    }

    /** Body text: line endings become \n, other control characters (tabs aside) are dropped. */
    static String body(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace("\r", "\n").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
    }
}