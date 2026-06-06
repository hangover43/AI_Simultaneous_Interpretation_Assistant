package com.interpretation.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterpretationWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void audioChunksProduceSegmentAndRevisionEvents() throws Exception {
        String sessionId = createSession();
        CountDownLatch segmentLatch = new CountDownLatch(1);
        CountDownLatch revisionLatch = new CountDownLatch(1);
        List<String> messageTypes = Collections.synchronizedList(new ArrayList<>());

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.sendMessage(new TextMessage("""
                        {
                          "type": "audio_start",
                          "sessionId": "%s",
                          "audio": {
                            "format": "webm-opus",
                            "sampleRate": 48000,
                            "channels": 1
                          }
                        }
                        """.formatted(sessionId)));

                for (int i = 0; i < 3; i++) {
                    session.sendMessage(new TextMessage("""
                            {
                              "type": "audio_chunk",
                              "sessionId": "%s",
                              "sequence": %d,
                              "timestampMs": %d,
                              "payloadBase64": "dGVzdA=="
                            }
                            """.formatted(sessionId, i, System.currentTimeMillis())));
                }
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                JsonNode root = objectMapper.readTree(message.getPayload());
                String type = root.path("type").asText();
                messageTypes.add(type);
                if ("segment_final".equals(type)) {
                    segmentLatch.countDown();
                }
                if ("segment_revision".equals(type)) {
                    revisionLatch.countDown();
                }
            }
        }, uri("/ws/interpretation?sessionId=" + sessionId).toString()).get(5, TimeUnit.SECONDS);

        try {
            assertThat(segmentLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(revisionLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(messageTypes).contains("connected", "audio_ready", "audio_ack", "segment_final", "live_translation", "segment_revision");
        } finally {
            session.close();
        }
    }

    private String createSession() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("""
                {
                  "topic": "AI 技术分享",
                  "sourceLanguage": "auto",
                  "targetLanguage": "zh-CN",
                  "revisionWindowSize": 8
                }
                """, headers);

        String response = restTemplate.postForObject(uri("/api/sessions"), request, String.class);
        JsonNode root = objectMapper.readTree(response);
        return root.path("sessionId").asText();
    }

    private URI uri(String path) {
        String scheme = path.startsWith("/ws/") ? "ws" : "http";
        return URI.create(scheme + "://127.0.0.1:" + port + path);
    }
}
