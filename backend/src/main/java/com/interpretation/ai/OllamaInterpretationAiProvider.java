package com.interpretation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interpretation.config.OllamaProperties;
import com.interpretation.model.GlossaryTerm;
import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
public class OllamaInterpretationAiProvider implements InterpretationAiProvider {

    private final ObjectMapper objectMapper;
    private final OllamaProperties properties;
    private final HttpClient httpClient;

    public OllamaInterpretationAiProvider(ObjectMapper objectMapper, OllamaProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Segment> demoSegments(InterpretationSession session) {
        return List.of(
                translateText(session, "seg_001", "The model reduces inference latency."),
                translateText(session, "seg_002", "We use streaming output to improve responsiveness."),
                translateText(session, "seg_003", "The glossary keeps technical terms consistent.")
        );
    }

    @Override
    public Segment translateText(InterpretationSession session, String segmentId, String sourceText) {
        return translated(segmentId, sourceText, session);
    }

    @Override
    public Optional<Segment> segmentFromAudioChunk(InterpretationSession session, int segmentIndex) {
        return switch (segmentIndex) {
            case 1 -> Optional.of(translated("seg_audio_001", "The speaker is talking about inference latency.", session));
            case 2 -> Optional.of(translated("seg_audio_002", "The system keeps a short context window.", session));
            case 3 -> Optional.of(translated("seg_audio_003", "Previous translations can be refined later.", session));
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Segment> revisionFor(InterpretationSession session, Segment segment) {
        if (!"seg_001".equals(segment.id()) && !"seg_audio_001".equals(segment.id()) && !"test_segment".equals(segment.id())) {
            return Optional.empty();
        }

        String prompt = """
                你是实时同声传译系统的译文修正器。
                请根据 topic 和术语表，优化下面这条中文译文。
                只输出修正后的中文译文，不要解释。

                Topic: %s
                术语表:
                %s

                原文:
                %s

                当前译文:
                %s
                """.formatted(session.topic(), glossaryText(session.glossary()), segment.sourceText(), segment.translation());

        String revisedTranslation = chat(prompt);
        return Optional.of(new Segment(segment.id(), segment.sourceText(), revisedTranslation, true));
    }

    private Segment translated(String id, String sourceText, InterpretationSession session) {
        String prompt = """
                你是实时同声传译系统的翻译模块。
                请将下面的原文翻译为简体中文，要求准确、自然、适合字幕显示。
                如果术语表中存在对应术语，请优先使用术语表译法。
                只输出中文译文，不要解释。

                Topic: %s
                术语表:
                %s

                原文:
                %s
                """.formatted(session.topic(), glossaryText(session.glossary()), sourceText);

        return new Segment(id, sourceText, chat(prompt), false);
    }

    private String chat(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", properties.getModel());
            requestBody.put("stream", false);
            requestBody.put("think", false);
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/api/chat"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return stripThinking(root.path("message").path("content").asText().trim());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Ollama.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama call was interrupted.", exception);
        }
    }

    private static String glossaryText(List<GlossaryTerm> glossary) {
        if (glossary == null || glossary.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (GlossaryTerm term : glossary) {
            builder.append("- ")
                    .append(term.source())
                    .append(" -> ")
                    .append(term.target())
                    .append('\n');
        }
        return builder.toString();
    }

    private static String stripThinking(String content) {
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
