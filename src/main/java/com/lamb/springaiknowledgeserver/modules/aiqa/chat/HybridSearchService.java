package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentChunkRepository;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.KeywordChunkRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.lamb.springaiknowledgeserver.modules.system.config.SystemConfigService;

@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final DocumentChunkRepository documentChunkRepository;
    private final SystemConfigService systemConfigService;

    @Value("${app.hybrid.vector-top-k:6}")
    private int vectorTopK;

    @Value("${app.hybrid.vector-similarity-threshold:0.2}")
    private double vectorSimilarityThreshold;

    @Value("${app.hybrid.keyword-top-k:6}")
    private int keywordTopK;

    @Value("${app.keyword.ts-config:simple}")
    private String defaultTsConfig;

    @Value("${app.hybrid.vector-weight:0.7}")
    private double vectorWeight;

    @Value("${app.hybrid.keyword-weight:0.3}")
    private double keywordWeight;

    @Value("${app.hybrid.top-k:6}")
    private int fusedTopK;

    public List<HybridChunk> search(String roleName, String query) {
        List<VectorHit> vectorHits = vectorSearch(roleName, query);
        List<KeywordHit> keywordHits = keywordSearch(roleName, query);

        Map<Long, HybridChunkBuilder> merged = new LinkedHashMap<>();
        for (VectorHit hit : vectorHits) {
            HybridChunkBuilder builder = merged.computeIfAbsent(hit.chunkId, id -> new HybridChunkBuilder());
            builder.applyVector(hit);
        }
        for (KeywordHit hit : keywordHits) {
            HybridChunkBuilder builder = merged.computeIfAbsent(hit.chunkId, id -> new HybridChunkBuilder());
            builder.applyKeyword(hit);
        }

        double resolvedVectorWeight = resolveVectorWeight();
        double resolvedKeywordWeight = resolveKeywordWeight();
        double totalWeight = Math.max(0, resolvedVectorWeight) + Math.max(0, resolvedKeywordWeight);
        double vWeight = totalWeight > 0 ? Math.max(0, resolvedVectorWeight) / totalWeight : 0.5;
        double kWeight = totalWeight > 0 ? Math.max(0, resolvedKeywordWeight) / totalWeight : 0.5;

        List<HybridChunk> results = new ArrayList<>();
        for (HybridChunkBuilder builder : merged.values()) {
            builder.finalizeScore(vWeight, kWeight);
            results.add(builder.build());
        }
        results.sort(Comparator.comparingDouble(HybridChunk::combinedScore).reversed());
        int resolvedTopK = resolveHybridTopK();
        int limit = resolvedTopK <= 0 ? results.size() : Math.min(resolvedTopK, results.size());
        return results.subList(0, limit);
    }

    private List<VectorHit> vectorSearch(String roleName, String query) {
        int topK = resolveVectorTopK();
        double threshold = resolveVectorThreshold();
        if (topK <= 0) {
            topK = SearchRequest.DEFAULT_TOP_K;
        }
        if (threshold <= 0) {
            threshold = SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;
        }
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(threshold)
            .filterExpression(filterBuilder.in("roleNames", roleName).build())
            .build();
        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);
        if (results.isEmpty()) {
            return List.of();
        }
        List<VectorHit> hits = new ArrayList<>();
        double maxSimilarity = 0.0;
        for (org.springframework.ai.document.Document doc : results) {
            Map<String, Object> metadata = doc.getMetadata();
            Long chunkId = extractLong(metadata.get("chunkId"));
            Long documentId = extractLong(metadata.get("documentId"));
            if (chunkId == null || documentId == null) {
                continue;
            }
            double distance = doc.getScore() == null ? Double.POSITIVE_INFINITY : doc.getScore();
            double similarity = distance == Double.POSITIVE_INFINITY ? 0.0 : 1.0 / (1.0 + distance);
            maxSimilarity = Math.max(maxSimilarity, similarity);
            VectorHit hit = new VectorHit(
                chunkId,
                documentId,
                doc.getText(),
                extractInt(metadata.get("pageNumber"), 1),
                extractInt(metadata.get("chunkIndex"), 0),
                extractInt(metadata.get("startOffset"), 0),
                extractInt(metadata.get("endOffset"), 0),
                Objects.toString(metadata.get("title"), null),
                Objects.toString(metadata.get("fileName"), null),
                Objects.toString(metadata.get("contentType"), null),
                similarity
            );
            hits.add(hit);
        }
        if (maxSimilarity <= 0) {
            return hits;
        }
        for (VectorHit hit : hits) {
            hit.normalizedScore = hit.score / maxSimilarity;
        }
        return hits;
    }

    private List<KeywordHit> keywordSearch(String roleName, String query) {
        int limit = Math.max(1, resolveKeywordTopK());
        String config = resolveTsConfig();
        List<KeywordChunkRow> rows = documentChunkRepository.searchChunksByKeyword(
            List.of(roleName),
            config,
            query,
            limit
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        double maxScore = 0.0;
        List<KeywordHit> hits = new ArrayList<>();
        for (KeywordChunkRow row : rows) {
            double score = row.getScore() == null ? 0.0 : row.getScore();
            maxScore = Math.max(maxScore, score);
            KeywordHit hit = new KeywordHit(
                row.getChunkId(),
                row.getDocumentId(),
                row.getContent(),
                row.getPageNumber() == null ? 1 : row.getPageNumber(),
                row.getChunkIndex() == null ? 0 : row.getChunkIndex(),
                row.getStartOffset() == null ? 0 : row.getStartOffset(),
                row.getEndOffset() == null ? 0 : row.getEndOffset(),
                row.getTitle(),
                row.getFileName(),
                row.getContentType(),
                score
            );
            hits.add(hit);
        }
        if (maxScore <= 0) {
            return hits;
        }
        for (KeywordHit hit : hits) {
            hit.normalizedScore = hit.score / maxScore;
        }
        return hits;
    }

    private Long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int extractInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int resolveVectorTopK() {
        return systemConfigService.getInt("hybrid.vectorTopK", vectorTopK);
    }

    private double resolveVectorThreshold() {
        return systemConfigService.getDouble("hybrid.vectorSimilarityThreshold", vectorSimilarityThreshold);
    }

    private int resolveKeywordTopK() {
        return systemConfigService.getInt("hybrid.keywordTopK", keywordTopK);
    }

    private String resolveTsConfig() {
        String value = systemConfigService.getString("keyword.tsConfig", defaultTsConfig);
        return (value == null || value.isBlank()) ? "simple" : value;
    }

    private double resolveVectorWeight() {
        return systemConfigService.getDouble("hybrid.vectorWeight", vectorWeight);
    }

    private double resolveKeywordWeight() {
        return systemConfigService.getDouble("hybrid.keywordWeight", keywordWeight);
    }

    private int resolveHybridTopK() {
        return systemConfigService.getInt("hybrid.topK", fusedTopK);
    }

    public record HybridChunk(
        Long chunkId,
        Long documentId,
        String content,
        int pageNumber,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String title,
        String fileName,
        String contentType,
        double vectorScore,
        double keywordScore,
        double combinedScore
    ) {}

    private static final class VectorHit {
        private final Long chunkId;
        private final Long documentId;
        private final String content;
        private final int pageNumber;
        private final int chunkIndex;
        private final int startOffset;
        private final int endOffset;
        private final String title;
        private final String fileName;
        private final String contentType;
        private final double score;
        private double normalizedScore;

        private VectorHit(
            Long chunkId,
            Long documentId,
            String content,
            int pageNumber,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String title,
            String fileName,
            String contentType,
            double score
        ) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.content = content;
            this.pageNumber = pageNumber;
            this.chunkIndex = chunkIndex;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.title = title;
            this.fileName = fileName;
            this.contentType = contentType;
            this.score = score;
            this.normalizedScore = score;
        }
    }

    private static final class KeywordHit {
        private final Long chunkId;
        private final Long documentId;
        private final String content;
        private final int pageNumber;
        private final int chunkIndex;
        private final int startOffset;
        private final int endOffset;
        private final String title;
        private final String fileName;
        private final String contentType;
        private final double score;
        private double normalizedScore;

        private KeywordHit(
            Long chunkId,
            Long documentId,
            String content,
            int pageNumber,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String title,
            String fileName,
            String contentType,
            double score
        ) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.content = content;
            this.pageNumber = pageNumber;
            this.chunkIndex = chunkIndex;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.title = title;
            this.fileName = fileName;
            this.contentType = contentType;
            this.score = score;
            this.normalizedScore = score;
        }
    }

    private static final class HybridChunkBuilder {
        private Long chunkId;
        private Long documentId;
        private String content;
        private int pageNumber;
        private int chunkIndex;
        private int startOffset;
        private int endOffset;
        private String title;
        private String fileName;
        private String contentType;
        private double vectorScore;
        private double keywordScore;
        private double combinedScore;

        private void applyVector(VectorHit hit) {
            this.chunkId = hit.chunkId;
            this.documentId = hit.documentId;
            this.content = hit.content;
            this.pageNumber = hit.pageNumber;
            this.chunkIndex = hit.chunkIndex;
            this.startOffset = hit.startOffset;
            this.endOffset = hit.endOffset;
            this.title = firstNonNull(this.title, hit.title);
            this.fileName = firstNonNull(this.fileName, hit.fileName);
            this.contentType = firstNonNull(this.contentType, hit.contentType);
            this.vectorScore = Math.max(this.vectorScore, hit.normalizedScore);
        }

        private void applyKeyword(KeywordHit hit) {
            this.chunkId = hit.chunkId;
            this.documentId = hit.documentId;
            if (this.content == null || this.content.isBlank()) {
                this.content = hit.content;
            }
            this.pageNumber = hit.pageNumber;
            this.chunkIndex = hit.chunkIndex;
            this.startOffset = hit.startOffset;
            this.endOffset = hit.endOffset;
            this.title = firstNonNull(this.title, hit.title);
            this.fileName = firstNonNull(this.fileName, hit.fileName);
            this.contentType = firstNonNull(this.contentType, hit.contentType);
            this.keywordScore = Math.max(this.keywordScore, hit.normalizedScore);
        }

        private void finalizeScore(double vectorWeight, double keywordWeight) {
            this.combinedScore = vectorWeight * vectorScore + keywordWeight * keywordScore;
        }

        private HybridChunk build() {
            return new HybridChunk(
                chunkId,
                documentId,
                content,
                pageNumber,
                chunkIndex,
                startOffset,
                endOffset,
                title,
                fileName,
                contentType,
                vectorScore,
                keywordScore,
                combinedScore
            );
        }

        private String firstNonNull(String current, String candidate) {
            return current != null ? current : candidate;
        }
    }
}




