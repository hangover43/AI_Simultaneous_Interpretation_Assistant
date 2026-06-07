package com.interpretation.dto;

import jakarta.validation.constraints.NotBlank;

public record AiTranslateTestRequest(
        String topic,
        @NotBlank String text
) {
}
