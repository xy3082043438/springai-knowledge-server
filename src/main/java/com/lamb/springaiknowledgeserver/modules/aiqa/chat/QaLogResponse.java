package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLog;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QaLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String question;
    private String answer;
    private String roleName;
    private Integer topK;
    private String retrievalJson;
    private Instant createdAt;

    public static QaLogResponse from(QaLog log) {
        return new QaLogResponse(
            log.getId(),
            log.getUserId(),
            log.getUsername(),
            log.getQuestion(),
            log.getAnswer(),
            log.getRoleName(),
            log.getTopK(),
            log.getRetrievalJson(),
            log.getCreatedAt()
        );
    }
}


