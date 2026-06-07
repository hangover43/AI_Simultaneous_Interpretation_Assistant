package com.interpretation.ai;

import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockInterpretationAiProvider implements InterpretationAiProvider {

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
        return switch (sourceText) {
            case "The model reduces inference latency." -> new Segment(segmentId, sourceText, "该模型降低了推理延迟。", false);
            case "We use streaming output to improve responsiveness." -> new Segment(segmentId, sourceText, "我们使用流式输出来提升响应速度。", false);
            case "The glossary keeps technical terms consistent." -> new Segment(segmentId, sourceText, "术语表可以保持技术术语的一致性。", false);
            case "The speaker is talking about inference latency." -> new Segment(segmentId, sourceText, "演讲者正在讨论推理延迟。", false);
            case "The system keeps a short context window." -> new Segment(segmentId, sourceText, "系统会保留一个较短的上下文窗口。", false);
            case "Previous translations can be refined later." -> new Segment(segmentId, sourceText, "之前的翻译可以在之后被精修。", false);
            default -> new Segment(segmentId, sourceText, "【mock 翻译】" + sourceText, false);
        };
    }

    @Override
    public Optional<Segment> segmentFromAudioChunk(InterpretationSession session, int segmentIndex) {
        return switch (segmentIndex) {
            case 1 -> Optional.of(translateText(session, "seg_audio_001", "The speaker is talking about inference latency."));
            case 2 -> Optional.of(translateText(session, "seg_audio_002", "The system keeps a short context window."));
            case 3 -> Optional.of(translateText(session, "seg_audio_003", "Previous translations can be refined later."));
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Segment> revisionFor(InterpretationSession session, Segment segment) {
        if ("test_segment".equals(segment.id())) {
            return Optional.of(new Segment(
                    segment.id(),
                    segment.sourceText(),
                    "【mock 修正】" + segment.translation(),
                    true
            ));
        }

        if ("seg_audio_001".equals(segment.id())) {
            return Optional.of(new Segment(
                    "seg_audio_001",
                    "The speaker is talking about inference latency.",
                    "演讲者正在讨论模型推理延迟。",
                    true
            ));
        }

        if ("seg_001".equals(segment.id())) {
            return Optional.of(new Segment(
                    "seg_001",
                    "The model reduces inference latency.",
                    "该模型降低了模型推理延迟。",
                    true
            ));
        }

        return Optional.empty();
    }
}
