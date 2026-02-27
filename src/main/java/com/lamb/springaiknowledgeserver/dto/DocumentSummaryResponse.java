package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.Document;
import com.lamb.springaiknowledgeserver.entity.DocumentStatus;
import com.lamb.springaiknowledgeserver.entity.Role;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentSummaryResponse {

    private Long id;
    private String title;
    private String fileName;
    private String contentType;
    private long fileSize;
    private DocumentStatus status;
    private Set<String> allowedRoles;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentSummaryResponse from(Document document) {
        return new DocumentSummaryResponse(
            document.getId(),
            document.getTitle(),
            document.getFileName(),
            document.getContentType(),
            document.getFileSize(),
            document.getStatus(),
            document.getAllowedRoles().stream().map(Role::getName).collect(Collectors.toSet()),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
}
