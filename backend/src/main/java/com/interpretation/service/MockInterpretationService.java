package com.interpretation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MockInterpretationService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);

    public MockInterpretationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void startMockStream(WebSocketSession socketSession, InterpretationSession interpretationSession) {
        List<Segment> segments = List.of(
                new Segment("seg_001", "The model reduces inference latency.", "该模型降低了推理延迟。", false),
                new Segment("seg_002", "We use streaming output to improve responsiveness.", "我们使用流式输出来提升响应速度。", false),
                new Segment("seg_003", "The glossary keeps technical terms consistent.", "术语表可以保持技术术语的一致性。", false)
        );

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            executorService.schedule(() -> sendFinalSegment(socketSession, interpretationSession, segment), i * 1600L, TimeUnit.MILLISECONDS);
        }

        Segment revised = new Segment(
                "seg_001",
                "The model reduces inference latency.",
                "该模型降低了模型推理延迟。",
                true
        );
        executorService.schedule(() -> sendRevision(socketSession, interpretationSession, revised), 5600L, TimeUnit.MILLISECONDS);
    }

    private void sendFinalSegment(WebSocketSession socketSession, InterpretationSession interpretationSession, Segment segment) {
        interpretationSession.addSegment(segment);
        send(socketSession, segmentMessage("segment_final", interpretationSession.sessionId(), segment));
        send(socketSession, liveTranslationMessage(interpretationSession.sessionId(), segment));
    }

    private void sendRevision(WebSocketSession socketSession, InterpretationSession interpretationSession, Segment segment) {
        interpretationSession.reviseSegment(segment);
        send(socketSession, segmentMessage("segment_revision", interpretationSession.sessionId(), segment));
    }

    private ObjectNode segmentMessage(String type, String sessionId, Segment segment) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", type);
        root.put("sessionId", sessionId);
        ObjectNode segmentNode = root.putObject("segment");
        segmentNode.put("id", segment.id());
        segmentNode.put("sourceText", segment.sourceText());
        segmentNode.put("translation", segment.translation());
        segmentNode.put("revised", segment.revised());
        return root;
    }

    private ObjectNode liveTranslationMessage(String sessionId, Segment segment) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "live_translation");
        root.put("sessionId", sessionId);
        root.put("segmentId", segment.id());
        root.put("translation", segment.translation());
        return root;
    }

    private void send(WebSocketSession socketSession, ObjectNode payload) {
        if (!socketSession.isOpen()) {
            return;
        }
        try {
            socketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException ignored) {
            // The browser may close the socket while mock events are still scheduled.
        }
    }
}
