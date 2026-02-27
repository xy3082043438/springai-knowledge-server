package com.lamb.springaiknowledgeserver.service;

import com.lamb.springaiknowledgeserver.dto.DocumentResponse;
import com.lamb.springaiknowledgeserver.dto.QaResponse;
import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.Role;
import com.lamb.springaiknowledgeserver.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QaService {

    private static final String DEFAULT_NO_ANSWER = "未在知识库中找到相关信息。";

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;

    @Value("${app.rag.top-k:6}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.2}")
    private double similarityThreshold;

    public QaResponse answer(String roleName, String question) {
        List<org.springframework.ai.document.Document> chunks = retrieveChunks(roleName, question);
        if (chunks.isEmpty()) {
            return new QaResponse(DEFAULT_NO_ANSWER, List.of());
        }

        String context = buildContext(chunks);
        String answer = chatClient.prompt()
            .system(systemPrompt())
            .user(userPrompt(question, context))
            .call()
            .content();
        if (answer == null || answer.isBlank()) {
            answer = DEFAULT_NO_ANSWER;
        }

        List<DocumentResponse> documents = resolveDocuments(chunks, roleName);
        return new QaResponse(answer, documents);
    }

    private List<org.springframework.ai.document.Document> retrieveChunks(String roleName, String question) {
        int topKValue = topK <= 0 ? SearchRequest.DEFAULT_TOP_K : topK;
        double threshold = similarityThreshold <= 0
            ? SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL
            : similarityThreshold;
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
            .query(question)
            .topK(topKValue)
            .similarityThreshold(threshold)
            .filterExpression(filterBuilder.in("roleNames", roleName).build())
            .build();
        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<org.springframework.ai.document.Document> filtered = new ArrayList<>();
        for (org.springframework.ai.document.Document doc : results) {
            if (hasRoleAccess(doc, roleName)) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    private boolean hasRoleAccess(org.springframework.ai.document.Document document, String roleName) {
        Object roles = document.getMetadata().get("roleNames");
        if (roles instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (roleName.equals(String.valueOf(value))) {
                    return true;
                }
            }
            return false;
        }
        if (roles == null) {
            return false;
        }
        return roleName.equals(String.valueOf(roles));
    }

    private String buildContext(List<org.springframework.ai.document.Document> chunks) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (org.springframework.ai.document.Document chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata();
            String title = Objects.toString(metadata.get("title"), "未知文档");
            Object page = metadata.get("pageNumber");
            String pageText = page == null ? "" : "第" + page + "页";
            builder.append("片段 ").append(index++).append("（")
                .append(title);
            if (!pageText.isBlank()) {
                builder.append("，").append(pageText);
            }
            builder.append("）:\n");
            builder.append(chunk.getText() == null ? "" : chunk.getText());
            builder.append("\n\n");
        }
        return builder.toString().trim();
    }

    private List<DocumentResponse> resolveDocuments(List<org.springframework.ai.document.Document> chunks, String roleName) {
        Set<Long> docIds = new LinkedHashSet<>();
        for (org.springframework.ai.document.Document chunk : chunks) {
            Long id = extractLong(chunk.getMetadata().get("documentId"));
            if (id != null) {
                docIds.add(id);
            }
        }
        if (docIds.isEmpty()) {
            return List.of();
        }
        List<Document> documents = documentRepository.findAllById(docIds);
        List<DocumentResponse> responses = new ArrayList<>();
        for (Long id : docIds) {
            Document document = findDocument(documents, id);
            if (document == null || !hasRoleAccess(document, roleName)) {
                continue;
            }
            responses.add(DocumentResponse.from(document));
        }
        return responses;
    }

    private Document findDocument(List<Document> documents, Long id) {
        for (Document document : documents) {
            if (Objects.equals(document.getId(), id)) {
                return document;
            }
        }
        return null;
    }

    private boolean hasRoleAccess(Document document, String roleName) {
        for (Role role : document.getAllowedRoles()) {
            if (roleName.equals(role.getName())) {
                return true;
            }
        }
        return false;
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

    private String systemPrompt() {
        return """
            你是企业知识库问答助手。请严格基于提供的“上下文”回答问题。
            如果上下文没有相关信息，请直接回答“未在知识库中找到相关信息。”不要编造答案。
            回答要简洁、准确、面向企业内部场景。
            """;
    }

    private String userPrompt(String question, String context) {
        return """
            问题:
            %s

            上下文:
            %s
            """.formatted(question, context);
    }
}
