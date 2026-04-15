package com.lamb.springaiknowledgeserver.modules.aiqa.chat;

import java.time.Instant;

public interface QaQuestionActivityRow {
    Instant getCreatedAt();
    String getQuestion();
}


