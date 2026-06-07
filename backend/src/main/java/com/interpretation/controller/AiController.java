package com.interpretation.controller;

import com.interpretation.ai.InterpretationAiProvider;
import com.interpretation.config.OllamaProperties;
import com.interpretation.dto.AiProviderResponse;
import com.interpretation.dto.AiReviseTestRequest;
import com.interpretation.dto.AiTestResponse;
import com.interpretation.dto.AiTranslateTestRequest;
import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;
import com.interpretation.service.GlossaryService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final Environment environment;
    private final OllamaProperties ollamaProperties;
    private final GlossaryService glossaryService;
    private final InterpretationAiProvider aiProvider;

    public AiController(
            Environment environment,
            OllamaProperties ollamaProperties,
            GlossaryService glossaryService,
            InterpretationAiProvider aiProvider
    ) {
        this.environment = environment;
        this.ollamaProperties = ollamaProperties;
        this.glossaryService = glossaryService;
        this.aiProvider = aiProvider;
    }

    @GetMapping("/provider")
    public AiProviderResponse provider() {
        return new AiProviderResponse(providerName(), ollamaProperties.getModel(), ollamaProperties.getBaseUrl());
    }

    @PostMapping("/translate-test")
    public AiTestResponse translateTest(@Valid @RequestBody AiTranslateTestRequest request) {
        InterpretationSession session = testSession(request.topic());
        Segment segment = aiProvider.translateText(session, "test_segment", request.text());
        return new AiTestResponse(providerName(), ollamaProperties.getModel(), segment.sourceText(), segment.translation(), false);
    }

    @PostMapping("/revise-test")
    public AiTestResponse reviseTest(@Valid @RequestBody AiReviseTestRequest request) {
        InterpretationSession session = testSession(request.topic());
        Segment original = new Segment("test_segment", request.sourceText(), request.translation(), false);
        Segment revised = aiProvider.revisionFor(session, original).orElse(original);
        return new AiTestResponse(providerName(), ollamaProperties.getModel(), revised.sourceText(), revised.translation(), revised.revised());
    }

    private InterpretationSession testSession(String topic) {
        String normalizedTopic = topic == null || topic.isBlank() ? "通用会议" : topic;
        return new InterpretationSession(
                "test_session",
                normalizedTopic,
                "auto",
                "zh-CN",
                8,
                glossaryService.generate(normalizedTopic)
        );
    }

    private String providerName() {
        return environment.getProperty("ai.provider", "mock");
    }
}
