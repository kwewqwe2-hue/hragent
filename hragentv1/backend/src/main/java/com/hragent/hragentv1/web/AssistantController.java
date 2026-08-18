package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.AssistantDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.WebChatGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/assistant")
public class AssistantController {
    private final AuthService authService;
    private final WebChatGatewayService webChatGatewayService;

    public AssistantController(AuthService authService, WebChatGatewayService webChatGatewayService) {
        this.authService = authService;
        this.webChatGatewayService = webChatGatewayService;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantDtos.ChatResponse> chat(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AssistantDtos.ChatRequest request
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        var response = webChatGatewayService.chat(user, request.message());
        return ApiResponse.ok(new AssistantDtos.ChatResponse(response.answer(), List.of(), response.provider()));
    }
}
