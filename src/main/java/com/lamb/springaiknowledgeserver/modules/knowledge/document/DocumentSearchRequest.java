package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentSearchRequest {

    @NotBlank
    private String query;
}


