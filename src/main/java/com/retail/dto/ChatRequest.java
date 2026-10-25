package com.retail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 4000) String question,
        @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 180) String modelId
) {
}
