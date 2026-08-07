package com.hragent.hragentv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AssistantDtos {
    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message
    ) {
    }

    public record ChatResponse(
            String answer,
            List<String> evidenceTitles,
            String provider
    ) {
    }
}
