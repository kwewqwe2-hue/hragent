package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WebChatDtos {
    private WebChatDtos() {
    }

    public record MessageRequest(
            @NotBlank @Size(max = 1000) String message
    ) {
    }

    public record MessageResponse(
            String answer,
            String provider,
            String requestId
    ) {
    }

    public record AgentCallback(
            String msgtype,
            TextPayload text
    ) {
    }

    public record TextPayload(String content) {
    }
}
