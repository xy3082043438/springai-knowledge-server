package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentStatus;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentUpdateRequest {

    @Size(max = 200)
    private String title;

    private String content;

    private Set<@Size(max = 64) String> allowedRoles;

    private DocumentStatus status;
}



