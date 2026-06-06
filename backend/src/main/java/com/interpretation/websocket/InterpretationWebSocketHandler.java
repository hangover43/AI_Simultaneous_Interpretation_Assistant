package com.interpretation.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interpretation.model.InterpretationSession;
import com.interpretation.service.MockInterpretationService;
import com.interpretation.service.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class InterpretationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final MockInterpretationService mockInterpretationService;

    public InterpretationWebSocketHandler(
            ObjectMapper objectMapper,
            SessionService sessionService,
            MockInterpretationService mockInterpretationService
    ) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.mockInterpretationService = mockInterpretationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = queryParams(session.getUri()).get("sessionId");
        if (sessionId == null || sessionService.findSession(sessionId).isEmpty()) {
            sendError(session, sessionId, "SESSION_NOT_FOUND", "Session not found.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        ObjectNode connected = objectMapper.createObjectNode();
        connected.put("type", "connected");
        connected.put("sessionId", sessionId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connected)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession socketSession, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = text(root, "type");
        String sessionId = text(root, "sessionId");
        Optional<InterpretationSession> interpretationSession = sessionService.findSession(sessionId);
        if (interpretationSession.isEmpty()) {
            sendError(socketSession, sessionId, "SESSION_NOT_FOUND", "Session not found.");
            return;
        }

        if ("mock_start".equals(type) || "audio_start".equals(type)) {
            mockInterpretationService.startMockStream(socketSession, interpretationSession.get());
            return;
        }

        if ("audio_chunk".equals(type)) {
            sendAck(socketSession, sessionId, root.path("sequence").asInt(-1));
            return;
        }

        if ("audio_end".equals(type)) {
            sessionService.stopSession(sessionId);
            return;
        }

        sendError(socketSession, sessionId, "UNKNOWN_MESSAGE_TYPE", "Unknown message type: " + type);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Sessions are stopped explicitly by REST or audio_end. Socket close alone is not destructive.
    }

    private void sendAck(WebSocketSession socketSession, String sessionId, int sequence) throws IOException {
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("type", "audio_ack");
        ack.put("sessionId", sessionId);
        ack.put("sequence", sequence);
        socketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    private void sendError(WebSocketSession socketSession, String sessionId, String code, String message) throws IOException {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", "error");
        if (sessionId != null) {
            error.put("sessionId", sessionId);
        }
        error.put("code", code);
        error.put("message", message);
        socketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Map<String, String> queryParams(URI uri) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return Map.of();
        }
        return Arrays.stream(uri.getQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (a, b) -> b));
    }
}
