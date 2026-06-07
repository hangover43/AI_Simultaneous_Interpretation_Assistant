package com.interpretation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interpretation.ai.InterpretationAiProvider;
import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MockInterpretationService {

    private final ObjectMapper objectMapper;
    private final InterpretationAiProvider aiProvider;
    private final WhisperAsrService whisperAsrService;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private final ConcurrentMap<String, AtomicInteger> chunkCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> segmentCounters = new ConcurrentHashMap<>();

    public MockInterpretationService(
            ObjectMapper objectMapper,
            InterpretationAiProvider aiProvider,
            WhisperAsrService whisperAsrService
    ) {
        this.objectMapper = objectMapper;
        this.aiProvider = aiProvider;
        this.whisperAsrService = whisperAsrService;
    }

    public void startAudioStream(WebSocketSession socketSession, InterpretationSession interpretationSession) {
        chunkCounters.put(interpretationSession.sessionId(), new AtomicInteger(0));
        segmentCounters.put(interpretationSession.sessionId(), new AtomicInteger(0));

        ObjectNode ready = objectMapper.createObjectNode();
        ready.put("type", "audio_ready");
        ready.put("sessionId", interpretationSession.sessionId());
        send(socketSession, ready);
    }

    public void handleAudioChunk(
            WebSocketSession socketSession,
            InterpretationSession interpretationSession,
            int sequence,
            String payloadBase64
    ) {
        sendAudioAck(socketSession, interpretationSession.sessionId(), sequence);

        int chunkCount = chunkCounters
                .computeIfAbsent(interpretationSession.sessionId(), ignored -> new AtomicInteger(0))
                .incrementAndGet();

        var transcript = whisperAsrService.transcribe(interpretationSession.sessionId(), sequence, payloadBase64);
        if (transcript.isPresent()) {
            Segment segment = aiProvider.translateText(
                    interpretationSession,
                    "seg_asr_" + String.format("%03d", sequence),
                    transcript.get()
            );
            sendFinalSegment(socketSession, interpretationSession, segment);
            aiProvider.revisionFor(interpretationSession, segment)
                    .ifPresent(revised -> executorService.schedule(
                            () -> sendRevision(socketSession, interpretationSession, revised),
                            2400L,
                            TimeUnit.MILLISECONDS
                    ));
            return;
        }

        if (chunkCount % 3 != 0) {
            return;
        }

        aiProvider.segmentFromAudioChunk(interpretationSession, nextSegmentIndex(interpretationSession))
                .ifPresent(segment -> {
                    sendFinalSegment(socketSession, interpretationSession, segment);
                    aiProvider.revisionFor(interpretationSession, segment)
                            .ifPresent(revised -> executorService.schedule(
                                    () -> sendRevision(socketSession, interpretationSession, revised),
                                    2400L,
                                    TimeUnit.MILLISECONDS
                            ));
                });
    }

    public void stopAudioStream(String sessionId) {
        chunkCounters.remove(sessionId);
        segmentCounters.remove(sessionId);
    }

    public void startMockStream(WebSocketSession socketSession, InterpretationSession interpretationSession) {
        var segments = aiProvider.demoSegments(interpretationSession);

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            executorService.schedule(() -> sendFinalSegment(socketSession, interpretationSession, segment), i * 1600L, TimeUnit.MILLISECONDS);
        }

        if (!segments.isEmpty()) {
            aiProvider.revisionFor(interpretationSession, segments.get(0))
                    .ifPresent(revised -> executorService.schedule(
                            () -> sendRevision(socketSession, interpretationSession, revised),
                            5600L,
                            TimeUnit.MILLISECONDS
                    ));
        }
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

    private int nextSegmentIndex(InterpretationSession interpretationSession) {
        return segmentCounters
                .computeIfAbsent(interpretationSession.sessionId(), ignored -> new AtomicInteger(0))
                .incrementAndGet();
    }
}
