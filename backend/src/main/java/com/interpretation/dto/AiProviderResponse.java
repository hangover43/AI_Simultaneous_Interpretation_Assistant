package com.interpretation.dto;

public record AiProviderResponse(
        String provider,
        String model,
        String baseUrl
) {
}
