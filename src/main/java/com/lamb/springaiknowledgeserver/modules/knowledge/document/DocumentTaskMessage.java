package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long documentId;
    private String action; // "PARSE" 或 "REINDEX"
    private String contentType;
    private String fileName;
}
