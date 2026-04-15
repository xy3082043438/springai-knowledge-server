package com.lamb.springaiknowledgeserver.modules.knowledge.document;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentReindexResponse {

    private int total;
    private int success;
    private int failed;
    private List<Long> failedIds;
}


