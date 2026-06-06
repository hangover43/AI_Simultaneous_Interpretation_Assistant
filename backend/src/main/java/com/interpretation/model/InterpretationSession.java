package com.interpretation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InterpretationSession {

    private final String sessionId;
    private final String topic;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final int revisionWindowSize;
    private final List<GlossaryTerm> glossary;
    private final CopyOnWriteArrayList<Segment> segments = new CopyOnWriteArrayList<>();
    private volatile String status;

    public InterpretationSession(
            String sessionId,
            String topic,
            String sourceLanguage,
            String targetLanguage,
            int revisionWindowSize,
            List<GlossaryTerm> glossary
    ) {
        this.sessionId = sessionId;
        this.topic = topic;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.revisionWindowSize = revisionWindowSize;
        this.glossary = List.copyOf(glossary);
        this.status = "running";
    }

    public String sessionId() {
        return sessionId;
    }

    public String topic() {
        return topic;
    }

    public String sourceLanguage() {
        return sourceLanguage;
    }

    public String targetLanguage() {
        return targetLanguage;
    }

    public int revisionWindowSize() {
        return revisionWindowSize;
    }

    public List<GlossaryTerm> glossary() {
        return glossary;
    }

    public String status() {
        return status;
    }

    public void stop() {
        this.status = "stopped";
    }

    public List<Segment> segments() {
        return Collections.unmodifiableList(segments);
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public void reviseSegment(Segment revisedSegment) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).id().equals(revisedSegment.id())) {
                segments.set(i, revisedSegment);
                return;
            }
        }
    }

    public void clearSegments() {
        segments.clear();
    }

    public List<Segment> recentSegments() {
        int fromIndex = Math.max(0, segments.size() - revisionWindowSize);
        return new ArrayList<>(segments.subList(fromIndex, segments.size()));
    }
}
