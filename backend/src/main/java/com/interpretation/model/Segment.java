package com.interpretation.model;

public record Segment(
        String id,
        String sourceText,
        String translation,
        boolean revised
) {
}
