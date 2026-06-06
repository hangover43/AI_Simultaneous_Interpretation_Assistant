package com.interpretation.service;

import com.interpretation.model.GlossaryTerm;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class GlossaryService {

    public List<GlossaryTerm> generate(String topic) {
        String normalized = topic == null ? "" : topic.toLowerCase(Locale.ROOT);
        if (normalized.contains("ai") || normalized.contains("人工智能") || normalized.contains("技术")) {
            return List.of(
                    new GlossaryTerm("inference latency", "推理延迟"),
                    new GlossaryTerm("model distillation", "模型蒸馏"),
                    new GlossaryTerm("large language model", "大语言模型"),
                    new GlossaryTerm("retrieval augmented generation", "检索增强生成")
            );
        }
        if (normalized.contains("medical") || normalized.contains("医学")) {
            return List.of(
                    new GlossaryTerm("clinical trial", "临床试验"),
                    new GlossaryTerm("diagnosis", "诊断"),
                    new GlossaryTerm("treatment protocol", "治疗方案")
            );
        }
        return List.of(
                new GlossaryTerm("speaker", "演讲者"),
                new GlossaryTerm("session", "会议场次"),
                new GlossaryTerm("summary", "总结")
        );
    }
}
