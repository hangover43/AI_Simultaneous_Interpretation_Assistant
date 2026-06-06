package com.interpretation.dto;

import com.interpretation.model.GlossaryTerm;
import com.interpretation.model.InterpretationSession;

import java.util.List;

public record SessionResponse(
        String sessionId,
        String topic,
        String sourceLanguage,
        String targetLanguage,
        String status,
        List<GlossaryTerm> glossary
) {
    public static SessionResponse from(InterpretationSession session) {
        return new SessionResponse(
                session.sessionId(),
                session.topic(),
                session.sourceLanguage(),
                session.targetLanguage(),
                session.status(),
                session.glossary()
        );
    }
}
