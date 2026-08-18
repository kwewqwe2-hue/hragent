package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.WebChatDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.WebChatGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/web-chat")
public class WebChatController {
    private final AuthService authService;
    private final WebChatGatewayService webChatGatewayService;

    public WebChatController(AuthService authService, WebChatGatewayService webChatGatewayService) {
        this.authService = authService;
        this.webChatGatewayService = webChatGatewayService;
    }

    @PostMapping("/messages")
    public ApiResponse<WebChatDtos.MessageResponse> chat(
            HttpServletRequest servletRequest,
            @Valid @RequestBody WebChatDtos.MessageRequest request
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        return ApiResponse.ok(webChatGatewayService.chat(user, request.message()));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WebChatDtos.MessageResponse> chatWithAttachment(
            HttpServletRequest servletRequest,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "message", required = false) String message
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        return ApiResponse.ok(webChatGatewayService.chatWithAttachment(user, file, message));
    }
}
