package com.lamb.springaiknowledgeserver.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RestClient.Builder restClientBuilder;

    @Value("${app.rerank.enabled:true}")
    private boolean enabled;

    @Value("${app.rerank.base-url:https://api.siliconflow.cn/v1/rerank}")
    private String baseUrl;

    @Value("${app.rerank.model:BAAI/bge-reranker-v2-m3}")
    private String model;

    @Value("${app.rerank.top-n:6}")
    private int topN;

    @Value("${app.rerank.max-docs:30}")
    private int maxDocs;

    @Value("${app.rerank.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    @Value("${app.rerank.read-timeout-ms:5000}")
    private long readTimeoutMs;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public List<HybridSearchService.HybridChunk> rerank(
        String query,
        List<HybridSearchService.HybridChunk> candidates
    ) {
        if (!enabled || candidates == null || candidates.size() < 2) {
            return candidates == null ? List.of() : candidates;
        }
        int limit = Math.max(1, maxDocs);
        List<HybridSearchService.HybridChunk> limited = candidates.size() > limit
            ? candidates.subList(0, limit)
            : candidates;
        List<String> documents = new ArrayList<>();
        for (HybridSearchService.HybridChunk chunk : limited) {
            documents.add(chunk.content() == null ? "" : chunk.content());
        }

        int topNValue = topN <= 0 ? limited.size() : Math.min(topN, limited.size());
        RerankRequest request = new RerankRequest(model, query, documents, topNValue, false);
        RerankResponse response = callRerank(request);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return candidates;
        }

        List<HybridSearchService.HybridChunk> reranked = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        response.results().stream()
            .sorted((a, b) -> Double.compare(
                b.relevanceScore() == null ? 0.0 : b.relevanceScore(),
                a.relevanceScore() == null ? 0.0 : a.relevanceScore()
            ))
            .forEach(result -> {
                Integer index = result.index();
                if (index == null || index < 0 || index >= limited.size()) {
                    return;
                }
                used.add(index);
                HybridSearchService.HybridChunk origin = limited.get(index);
                double score = result.relevanceScore() == null ? origin.combinedScore() : result.relevanceScore();
                reranked.add(withCombinedScore(origin, score));
            });

        for (int i = 0; i < limited.size() && reranked.size() < topNValue; i++) {
            if (!used.contains(i)) {
                reranked.add(limited.get(i));
            }
        }

        return reranked;
    }

    private RerankResponse callRerank(RerankRequest request) {
        try {
            RestClient client = restClientBuilder
                .clone()
                .requestFactory(clientHttpRequestFactory())
                .build();
            return client.post()
                .uri(baseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey);
                    }
                })
                .body(request)
                .retrieve()
                .body(RerankResponse.class);
        } catch (Exception ex) {
            log.debug("Failed to call rerank API", ex);
            return null;
        }
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (connectTimeoutMs > 0) {
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        }
        if (readTimeoutMs > 0) {
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        }
        return factory;
    }

    private HybridSearchService.HybridChunk withCombinedScore(
        HybridSearchService.HybridChunk origin,
        double combinedScore
    ) {
        return new HybridSearchService.HybridChunk(
            origin.chunkId(),
            origin.documentId(),
            origin.content(),
            origin.pageNumber(),
            origin.chunkIndex(),
            origin.startOffset(),
            origin.endOffset(),
            origin.title(),
            origin.fileName(),
            origin.contentType(),
            origin.vectorScore(),
            origin.keywordScore(),
            combinedScore
        );
    }

    private record RerankRequest(
        String model,
        String query,
        List<String> documents,
        @JsonProperty("top_n") Integer topN,
        @JsonProperty("return_documents") Boolean returnDocuments
    ) {}

    private record RerankResponse(
        String id,
        List<RerankResult> results,
        Map<String, Object> meta
    ) {}

    private record RerankResult(
        Integer index,
        @JsonProperty("relevance_score") Double relevanceScore
    ) {}
}
