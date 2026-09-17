package com.hragent.hragentv1.web;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class EmployeeAgentController {
    public record Input(@NotBlank @Size(max=30000) String message) { }
    private final AgentIntegrationService identity;
    private final EmployeeAgentRouter router;
    public EmployeeAgentController(AgentIntegrationService identity,EmployeeAgentRouter router) { this.identity=identity; this.router=router; }
    @PostMapping("/internal/agent/v1/service-reply")
    public ApiResponse<?> reply(@RequestHeader("X-API-Key") String key, @RequestHeader("X-DingTalk-User-Id") String sender,
            @Valid @RequestBody Input input) {
        if (input.message().trim().matches("^绑定\\s+[A-Za-z0-9]{8}$")) {
            identity.bind(key,sender,null,new com.hragent.hragentv1.dto.AgentIntegrationDtos.BindRequest(input.message().trim().replaceFirst("^绑定\\s+", "")));
            return ApiResponse.ok(new EmployeeAgentRouter.Reply(true,"钉钉身份绑定成功，可以查询和办理本人有权限的员工服务。","employee-binding"));
        }
        return ApiResponse.ok(router.reply(identity.serviceEmployee(key,sender),input.message()));
    }
}
