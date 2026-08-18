package com.hragent.hragentv1.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(RequestCorrelation.HEADER));
        MDC.put("requestId", requestId);
        response.setHeader(RequestCorrelation.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }

    private String normalize(String value) {
        if (value != null && value.matches("[A-Za-z0-9._:-]{1,100}")) {
            return value;
        }
        return "HR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
