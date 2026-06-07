package com.interpretation.dto;

import jakarta.validation.constraints.NotBlank;

public record AiReviseTestRequest(
        String topic,
        @NotBlank String sourceText,
        @NotBlank String translation
) {
}
