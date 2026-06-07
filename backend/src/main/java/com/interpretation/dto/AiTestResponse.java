package com.interpretation.dto;

public record AiTestResponse(
        String provider,
        String model,
        String sourceText,
        String translation,
        boolean revised
) {
}
