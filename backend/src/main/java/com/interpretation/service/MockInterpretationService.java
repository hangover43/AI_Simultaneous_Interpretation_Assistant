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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MockInterpretationService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private final ConcurrentMap<String, AtomicInteger> chunkCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> segmentCounters = new ConcurrentHashMap<>();

    public MockInterpretationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void startAudioStream(WebSocketSession socketSession, InterpretationSession interpretationSession) {
        chunkCounters.put(interpretationSession.sessionId(), new AtomicInteger(0));
        segmentCounters.put(interpretationSession.sessionId(), new AtomicInteger(0));

        ObjectNode ready = objectMapper.createObjectNode();
        ready.put("type", "audio_ready");
        ready.put("sessionId", interpretationSession.sessionId());
        send(socketSession, ready);
    }

    public void handleAudioChunk(WebSocketSession socketSession, InterpretationSession interpretationSession, int sequence) {
        sendAudioAck(socketSession, interpretationSession.sessionId(), sequence);

        int chunkCount = chunkCounters
                .computeIfAbsent(interpretationSession.sessionId(), ignored -> new AtomicInteger(0))
                .incrementAndGet();

        if (chunkCount % 3 != 0) {
            return;
        }

        Segment segment = nextChunkDrivenSegment(interpretationSession);
        if (segment == null) {
            return;
        }

        sendFinalSegment(socketSession, interpretationSession, segment);

        if ("seg_audio_001".equals(segment.id())) {
            Segment revised = new Segment(
                    "seg_audio_001",
                    "The speaker is talking about inference latency.",
                    "演讲者正在讨论模型推理延迟。",
                    true
            );
            executorService.schedule(() -> sendRevision(socketSession, interpretationSession, revised), 2400L, TimeUnit.MILLISECONDS);
        }
    }

    public void stopAudioStream(String sessionId) {
        chunkCounters.remove(sessionId);
        segmentCounters.remove(sessionId);
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

    private Segment nextChunkDrivenSegment(InterpretationSession interpretationSession) {
        int index = segmentCounters
                .computeIfAbsent(interpretationSession.sessionId(), ignored -> new AtomicInteger(0))
                .incrementAndGet();

        return switch (index) {
            case 1 -> new Segment(
                    "seg_audio_001",
                    "The speaker is talking about inference latency.",
                    "演讲者正在讨论推理延迟。",
                    false
            );
            case 2 -> new Segment(
                    "seg_audio_002",
                    "The system keeps a short context window.",
                    "系统会保留一个较短的上下文窗口。",
                    false
            );
            case 3 -> new Segment(
                    "seg_audio_003",
                    "Previous translations can be refined later.",
                    "之前的翻译可以在之后被精修。",
                    false
            );
            default -> null;
        };
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

    private void sendAudioAck(WebSocketSession socketSession, String sessionId, int sequence) {
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "audio_ack");
        ack.put("sessionId", sessionId);
        ack.put("sequence", sequence);
        send(socketSession, ack);
    }
}
