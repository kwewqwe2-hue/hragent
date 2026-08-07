package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.ApiCallLog;
import com.hragent.hragentv1.domain.IntegrationApiKey;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.repo.ApiCallLogRepository;
import com.hragent.hragentv1.service.OpenApiService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiCallLogRepository apiCallLogRepository;
    private final OpenApiService openApiService;

    public GlobalExceptionHandler(ApiCallLogRepository apiCallLogRepository, OpenApiService openApiService) {
        this.apiCallLogRepository = apiCallLogRepository;
        this.openApiService = openApiService;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        logFailure(request, exception.getStatus().value(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        logFailure(request, HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = "X-API-Key".equalsIgnoreCase(exception.getHeaderName())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        String message = "Missing required header: " + exception.getHeaderName();
        logFailure(request, status.value(), message);
        return ResponseEntity.status(status).body(ApiResponse.fail(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String message = "请求格式错误，请检查 JSON 字段和日期格式（yyyy-MM-dd）";
        logFailure(request, HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request
    ) {
        log.debug("Client disconnected before response completed requestId={} method={} path={}",
                RequestCorrelation.currentId(), request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled application error requestId={} method={} path={}",
                RequestCorrelation.currentId(), request.getMethod(), request.getRequestURI(), exception);
        logFailure(request, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unhandled application error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("系统内部错误，请联系管理员。请求编号: " + RequestCorrelation.currentId()));
    }

    private void logFailure(HttpServletRequest request, int status, String message) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!path.startsWith(contextPath + "/internal/agent/v1")
                && !path.startsWith(contextPath + "/openapi/v1")) {
            return;
        }

        String rawApiKey = request.getHeader("X-API-Key");
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("API failure requestId={} method={} path={} status={} message={}",
                    RequestCorrelation.currentId(), request.getMethod(), path, status, message);
            return;
        }

        try {
            IntegrationApiKey key = openApiService.authenticate(rawApiKey, request.getMethod(), path);
            ApiCallLog apiCallLog = new ApiCallLog();
            apiCallLog.setTenantId(key.getTenantId());
            apiCallLog.setApiKeyId(key.getId());
            apiCallLog.setMethod(request.getMethod());
            apiCallLog.setPath(path);
            apiCallLog.setStatusCode(status);
            apiCallLog.setRequestId(RequestCorrelation.currentId());
            String detail = "[" + RequestCorrelation.currentId() + "] " + message;
            apiCallLog.setMessage(detail.substring(0, Math.min(600, detail.length())));
            apiCallLogRepository.save(apiCallLog);
        } catch (RuntimeException authenticationFailure) {
            log.warn("API failure could not be associated with a workspace requestId={} method={} path={} status={} message={}",
                    RequestCorrelation.currentId(), request.getMethod(), path, status, message);
        }
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
