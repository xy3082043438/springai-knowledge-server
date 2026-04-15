package com.lamb.springaiknowledgeserver.modules.aiqa.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "app_qa_feedback",
    indexes = {
        @Index(name = "idx_feedback_qalog", columnList = "qa_log_id"),
        @Index(name = "idx_feedback_user", columnList = "user_id"),
        @Index(name = "idx_feedback_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class QaFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qa_log_id", nullable = false)
    private Long qaLogId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 120)
    private String username;

    @Column(name = "helpful", nullable = false)
    private boolean helpful;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}


