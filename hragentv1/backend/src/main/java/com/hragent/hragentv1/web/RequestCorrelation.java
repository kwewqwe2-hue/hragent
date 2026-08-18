package com.hragent.hragentv1.web;

import org.slf4j.MDC;

public final class RequestCorrelation {
    public static final String HEADER = "X-Request-Id";

    private RequestCorrelation() {
    }

    public static String currentId() {
        String value = MDC.get("requestId");
        return value == null || value.isBlank() ? "-" : value;
    }
}
