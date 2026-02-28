package com.lamb.springaiknowledgeserver.dto;

import com.lamb.springaiknowledgeserver.entity.QaFeedback;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QaFeedbackResponse {

    private Long id;
    private Long qaLogId;
    private Long userId;
    private String username;
    private boolean helpful;
    private String comment;
    private Instant createdAt;

    public static QaFeedbackResponse from(QaFeedback feedback) {
        return new QaFeedbackResponse(
            feedback.getId(),
            feedback.getQaLogId(),
            feedback.getUserId(),
            feedback.getUsername(),
            feedback.isHelpful(),
            feedback.getComment(),
            feedback.getCreatedAt()
        );
    }
}
