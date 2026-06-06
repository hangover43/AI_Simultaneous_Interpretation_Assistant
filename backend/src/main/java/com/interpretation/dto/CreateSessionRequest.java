package com.interpretation.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank String topic,
        String sourceLanguage,
        String targetLanguage,
        Integer revisionWindowSize
) {
}
