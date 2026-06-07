package com.interpretation.ai;

import com.interpretation.model.InterpretationSession;
import com.interpretation.model.Segment;

import java.util.List;
import java.util.Optional;

public interface InterpretationAiProvider {

    List<Segment> demoSegments(InterpretationSession session);

    Segment translateText(InterpretationSession session, String segmentId, String sourceText);

    Optional<Segment> segmentFromAudioChunk(InterpretationSession session, int segmentIndex);

    Optional<Segment> revisionFor(InterpretationSession session, Segment segment);
}
