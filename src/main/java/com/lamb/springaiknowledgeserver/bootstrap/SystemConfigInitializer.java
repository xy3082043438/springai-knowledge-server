package com.lamb.springaiknowledgeserver.bootstrap;

import com.lamb.springaiknowledgeserver.config.PromptTemplates;
import com.lamb.springaiknowledgeserver.config.SystemBoundaryText;
import com.lamb.springaiknowledgeserver.entity.SystemConfig;
import com.lamb.springaiknowledgeserver.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class SystemConfigInitializer implements ApplicationRunner {

    private final SystemConfigRepository systemConfigRepository;

    @Value("${app.document.chunk-size}")
    private int defaultChunkSize;

    @Value("${app.document.chunk-overlap}")
    private int defaultChunkOverlap;

    @Value("${app.hybrid.top-k:6}")
    private int defaultHybridTopK;

    @Value("${app.hybrid.vector-top-k:6}")
    private int defaultVectorTopK;

    @Value("${app.hybrid.vector-similarity-threshold:0.2}")
    private double defaultVectorThreshold;

    @Value("${app.hybrid.keyword-top-k:6}")
    private int defaultKeywordTopK;

    @Value("${app.hybrid.vector-weight:0.7}")
    private double defaultVectorWeight;

    @Value("${app.hybrid.keyword-weight:0.3}")
    private double defaultKeywordWeight;

    @Value("${app.rag.answer-style:简洁、准确、专业}")
    private String defaultAnswerStyle;

    @Value("${app.rag.max-answer-chars:300}")
    private int defaultMaxAnswerChars;

    @Value("${app.rag.max-output-tokens:512}")
    private int defaultMaxOutputTokens;

    @Value("${app.rag.temperature:0.2}")
    private double defaultTemperature;

    @Value("${app.rag.top-p:0.9}")
    private double defaultTopP;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        ensureConfig("chunk.size", String.valueOf(defaultChunkSize), "文本切分长度");
        ensureConfig("chunk.overlap", String.valueOf(defaultChunkOverlap), "文本切分重叠长度");
        ensureConfig("hybrid.topK", String.valueOf(defaultHybridTopK), "混合检索返回数量");
        ensureConfig("hybrid.vectorTopK", String.valueOf(defaultVectorTopK), "向量检索 TopK");
        ensureConfig("hybrid.vectorSimilarityThreshold", String.valueOf(defaultVectorThreshold), "向量检索相似度阈值");
        ensureConfig("hybrid.keywordTopK", String.valueOf(defaultKeywordTopK), "关键词检索 TopK");
        ensureConfig("hybrid.vectorWeight", String.valueOf(defaultVectorWeight), "向量检索权重");
        ensureConfig("hybrid.keywordWeight", String.valueOf(defaultKeywordWeight), "关键词检索权重");
        ensureConfig("rag.answerStyle", defaultAnswerStyle, "回答风格");
        ensureConfig("rag.maxAnswerChars", String.valueOf(defaultMaxAnswerChars), "回答最大字数");
        ensureConfig("rag.maxOutputTokens", String.valueOf(defaultMaxOutputTokens), "最大输出 Token 数");
        ensureConfig("rag.temperature", String.valueOf(defaultTemperature), "生成温度");
        ensureConfig("rag.topP", String.valueOf(defaultTopP), "生成 TopP");
        ensureConfig("rag.prompt.system", PromptTemplates.SYSTEM_TEMPLATE.trim(), "System Prompt 模板");
        ensureConfig("rag.prompt.user", PromptTemplates.USER_TEMPLATE.trim(), "User Prompt 模板");
        ensureConfig("system.boundary", SystemBoundaryText.DEFAULT_BOUNDARY, "功能边界说明");
    }

    private void ensureConfig(String key, String value, String description) {
        if (systemConfigRepository.existsByConfigKey(key)) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);
    }
}
