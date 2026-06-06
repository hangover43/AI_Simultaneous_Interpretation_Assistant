package com.interpretation.service;

import com.interpretation.dto.CreateSessionRequest;
import com.interpretation.model.GlossaryTerm;
import com.interpretation.model.InterpretationSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SessionService {

    private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicInteger sequence = new AtomicInteger(1);
    private final ConcurrentMap<String, InterpretationSession> sessions = new ConcurrentHashMap<>();
    private final GlossaryService glossaryService;

    public SessionService(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    public InterpretationSession createSession(CreateSessionRequest request) {
        String sessionId = "sess_" + LocalDateTime.now().format(ID_TIME_FORMAT) + "_" + sequence.getAndIncrement();
        String sourceLanguage = valueOrDefault(request.sourceLanguage(), "auto");
        String targetLanguage = valueOrDefault(request.targetLanguage(), "zh-CN");
        int revisionWindowSize = request.revisionWindowSize() == null ? 8 : request.revisionWindowSize();
        List<GlossaryTerm> glossary = glossaryService.generate(request.topic());

        InterpretationSession session = new InterpretationSession(
                sessionId,
                request.topic(),
                sourceLanguage,
                targetLanguage,
                revisionWindowSize,
                glossary
        );
        sessions.put(sessionId, session);
        return session;
    }

    public Optional<InterpretationSession> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<InterpretationSession> stopSession(String sessionId) {
        InterpretationSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        session.stop();
        return Optional.of(session);
    }

    public boolean clearSegments(String sessionId) {
        InterpretationSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        session.clearSegments();
        return true;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
