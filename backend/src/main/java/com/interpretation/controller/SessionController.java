package com.interpretation.controller;

import com.interpretation.dto.CreateSessionRequest;
import com.interpretation.dto.SessionResponse;
import com.interpretation.model.InterpretationSession;
import com.interpretation.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest request) {
        InterpretationSession session = sessionService.createSession(request);
        return SessionResponse.from(session);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable String sessionId) {
        return sessionService.findSession(sessionId)
                .map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{sessionId}/stop")
    public ResponseEntity<Map<String, String>> stop(@PathVariable String sessionId) {
        return sessionService.stopSession(sessionId)
                .map(session -> ResponseEntity.ok(Map.of(
                        "sessionId", session.sessionId(),
                        "status", session.status()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sessionId}/segments")
    public ResponseEntity<Map<String, Object>> clearSegments(@PathVariable String sessionId) {
        boolean cleared = sessionService.clearSegments(sessionId);
        if (!cleared) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "cleared", true
        ));
    }
}
