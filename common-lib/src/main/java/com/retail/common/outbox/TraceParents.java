package com.retail.common.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.regex.Pattern;

/** Builds the W3C traceparent header value: 00-{32 hex trace id}-{16 hex span id}-{flags}. */
final class TraceParents {

    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

    private TraceParents() {}

    /** Null when there is no usable trace: the message is then sent without one. */
    static String format(String traceId, String spanId, Boolean sampled) {
        String trace = pad(traceId, 32);
        String span = pad(spanId, 16);
        if (trace == null || span == null) {
            return null;
        }
        return "00-" + trace + "-" + span + "-" + (Boolean.TRUE.equals(sampled) ? "01" : "00");
    }

    static String current(Tracer tracer) {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        return format(context.traceId(), context.spanId(), context.sampled());
    }

    private static String pad(String value, int length) {
        if (value == null) {
            return null;
        }
        String hex = value.trim().toLowerCase();
        if (hex.isEmpty() || hex.length() > length || !HEX.matcher(hex).matches() || hex.chars().allMatch(c -> c == '0')) {
            return null;                                           // W3C forbids an all-zero id
        }
        return "0".repeat(length - hex.length()) + hex;
    }
}