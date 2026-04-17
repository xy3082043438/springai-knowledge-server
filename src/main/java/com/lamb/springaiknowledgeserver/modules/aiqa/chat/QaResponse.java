package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import java.util.List;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QaResponse {

    private String answer;
    private List<DocumentResponse> documents;
    private List<QaSourceResponse> sources;
    private Long qaLogId;
    private Long sessionId;
}
