package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.WebChatDtos;
import com.hragent.hragentv1.service.WebChatGatewayService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/web-chat/callback")
public class WebChatCallbackController {
    private final WebChatGatewayService webChatGatewayService;

    public WebChatCallbackController(WebChatGatewayService webChatGatewayService) {
        this.webChatGatewayService = webChatGatewayService;
    }

    @PostMapping("/{requestId}")
    public ApiResponse<Void> callback(
            @PathVariable String requestId,
            @RequestParam String token,
            @RequestBody WebChatDtos.AgentCallback callback
    ) {
        webChatGatewayService.receive(requestId, token, callback);
        return ApiResponse.ok(null);
    }
}
