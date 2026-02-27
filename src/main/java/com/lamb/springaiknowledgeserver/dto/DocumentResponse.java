package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.Document;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import com.lamb.springaiknowledgeserver.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String title;
    private String content;
    private Set<String> allowedRoles;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
            document.getId(),
            document.getTitle(),
            document.getContent(),
            document.getAllowedRoles().stream().map(Role::getName).collect(Collectors.toSet()),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
}
