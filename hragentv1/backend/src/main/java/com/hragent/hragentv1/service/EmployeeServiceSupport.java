package com.hragent.hragentv1.service;

import com.hragent.hragentv1.web.AppException;
import java.net.URI;

public final class EmployeeServiceSupport {
    private EmployeeServiceSupport() { }
    public static boolean blank(String value) { return value == null || value.isBlank(); }
    public static String display(String value) { return blank(value) ? "未维护" : value; }
    public static void requireHttpUrl(String value) {
        if (blank(value)) return;
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("链接必须为有效的 http 或 https 地址");
        }
    }
}
