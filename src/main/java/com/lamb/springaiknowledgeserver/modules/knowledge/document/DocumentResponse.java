package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.Document;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentStatus;
import com.lamb.springaiknowledgeserver.modules.system.role.Role;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String title;
    private String content;
    private String fileName;
    private String contentType;
    private long fileSize;
    private DocumentStatus status;
    private Set<String> allowedRoles;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
            document.getId(),
            document.getTitle(),
            document.getContent(),
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



