package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WebChatDtos {
    private WebChatDtos() {
    }

    public record MessageRequest(
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 80) @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_-]+") String conversationId
    ) {
        public MessageRequest(String message) { this(message, null); }
    }

    public record MessageResponse(
            String answer,
            String provider,
            String requestId,
            java.util.List<ChatAction> actions,
            String details
    ) {
        public MessageResponse(String answer,String provider,String requestId) { this(answer,provider,requestId,java.util.List.of()); }
        public MessageResponse(String answer,String provider,String requestId,java.util.List<ChatAction> actions) { this(answer,provider,requestId,actions,null); }
    }
    public record ChatAction(String label,String type,String value) {}

    public record AgentCallback(
            String msgtype,
            TextPayload text
    ) {
    }

    public record TextPayload(String content) {
    }
}
