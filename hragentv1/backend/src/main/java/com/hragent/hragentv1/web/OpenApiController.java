package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.IntegrationApiKey;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.OpenApiDtos;
import com.hragent.hragentv1.service.OpenApiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/openapi/v1")
public class OpenApiController {
    private final OpenApiService openApiService;

    public OpenApiController(OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @GetMapping("/employees/{employeeNo}")
    public ApiResponse<OpenApiDtos.EmployeeResponse> employee(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String employeeNo
    ) {
        IntegrationApiKey key = openApiService.authenticate(apiKey, "GET", "/openapi/v1/employees/" + employeeNo);
        return ApiResponse.ok(openApiService.employee(key, employeeNo));
    }

    @GetMapping("/balances/{employeeNo}")
    public ApiResponse<OpenApiDtos.BalanceResponse> balances(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String employeeNo
    ) {
        IntegrationApiKey key = openApiService.authenticate(apiKey, "GET", "/openapi/v1/balances/" + employeeNo);
        return ApiResponse.ok(openApiService.balances(key, employeeNo));
    }

    @PostMapping("/employees/sync")
    public ApiResponse<OpenApiDtos.EmployeeResponse> syncEmployee(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody OpenApiDtos.EmployeePayload payload
    ) {
        IntegrationApiKey key = openApiService.authenticate(apiKey, "POST", "/openapi/v1/employees/sync");
        return ApiResponse.ok(openApiService.syncEmployee(key, payload));
    }
}
