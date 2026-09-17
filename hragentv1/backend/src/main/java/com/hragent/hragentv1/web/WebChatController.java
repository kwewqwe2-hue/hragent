package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.WebChatDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.WebChatGatewayService;
import com.hragent.hragentv1.service.EmployeeIntentUnderstanding;
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
    private final com.hragent.hragentv1.service.LifecycleService lifecycle;
    private final com.hragent.hragentv1.service.AssistantWorkspaceService workspace;

    public WebChatController(AuthService authService, WebChatGatewayService webChatGatewayService,
            com.hragent.hragentv1.service.LifecycleService lifecycle, com.hragent.hragentv1.service.AssistantWorkspaceService workspace) {
        this.authService = authService;
        this.webChatGatewayService = webChatGatewayService;
        this.lifecycle = lifecycle;
        this.workspace = workspace;
    }

    @PostMapping("/messages")
    public ApiResponse<WebChatDtos.MessageResponse> chat(
            HttpServletRequest servletRequest,
            @Valid @RequestBody WebChatDtos.MessageRequest request
    ) {
        UserAccount user = authService.requireLifecycleUser(servletRequest);
        // Stop commands take precedence over pending policy follow-ups and form fields.
        if (com.hragent.hragentv1.service.LifecycleService.cancellationRequested(request.message())) {
            webChatGatewayService.clearPolicyConversation(user,request.conversationId());
            webChatGatewayService.clearServiceConversation(user,request.conversationId());
            var stopped = lifecycle.guidedReply(user,request.message(),request.conversationId());
            if(stopped.isPresent())return ApiResponse.ok(stopped.get());
        }
        var clarification=EmployeeIntentUnderstanding.clarification(request.message());
        if(clarification.isPresent())return ApiResponse.ok(clarification.get());
        String resolved=webChatGatewayService.resolveServiceFollowup(user,request.message(),request.conversationId());
        String message=resolved==null?request.message():resolved;
        var guidance=webChatGatewayService.serviceGuidance(user,message,request.conversationId());
        if(guidance.isPresent())return finish(user,message,request.conversationId(),guidance.get());
        var policy = webChatGatewayService.policyConversation(user,message,request.conversationId());
        if(policy.isPresent())return finish(user,message,request.conversationId(),policy.get());
        var operation = workspace.reply(user, message);
        if(operation.isPresent()){webChatGatewayService.clearPolicyConversation(user,request.conversationId());return finish(user,message,request.conversationId(),operation.get());}
        var answer = lifecycle.guidedReply(user, message, request.conversationId());
        if (answer.isPresent()){webChatGatewayService.clearPolicyConversation(user,request.conversationId());return finish(user,message,request.conversationId(),answer.get());}
        if (com.hragent.hragentv1.service.LifecycleService.former(user))
            return ApiResponse.ok(new WebChatDtos.MessageResponse("这里可以继续办理你的离职后服务：补发离职证明、档案咨询、劳动关系证明及结算核对。直接告诉我要办哪一项即可。", "alumni-services", java.util.UUID.randomUUID().toString()));
        return finish(user,message,request.conversationId(),webChatGatewayService.chat(user, message, request.conversationId()));
    }
    private ApiResponse<WebChatDtos.MessageResponse> finish(UserAccount user,String message,String cid,WebChatDtos.MessageResponse response){
        webChatGatewayService.rememberService(user,message,cid,response);
        return ApiResponse.ok(response);
    }

    @org.springframework.web.bind.annotation.GetMapping("/status")
    public ApiResponse<java.util.Map<String, Object>> status(HttpServletRequest request) {
        return ApiResponse.ok(webChatGatewayService.status(authService.requireUser(request)));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WebChatDtos.MessageResponse> chatWithAttachment(
            HttpServletRequest servletRequest,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "message", required = false) String message,
            @RequestPart(value = "conversationId", required = false) String conversationId
    ) {
        UserAccount user = authService.requireUser(servletRequest);
        var certificate = lifecycle.certificateAttachment(user,conversationId,file,message);
        if (certificate.isPresent()) {
            webChatGatewayService.clearPolicyConversation(user,conversationId);
            return finish(user,message == null ? "" : message,conversationId,certificate.get());
        }
        var medical = lifecycle.medicalAttachment(user,conversationId,file,message);
        if(medical.isPresent())return ApiResponse.ok(medical.get());
        return ApiResponse.ok(webChatGatewayService.chatWithAttachment(user, file, message));
    }
}
