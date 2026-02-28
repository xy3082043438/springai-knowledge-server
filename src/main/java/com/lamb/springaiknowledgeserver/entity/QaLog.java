package com.lamb.springaiknowledgeserver.entity;

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
    name = "app_qa_log",
    indexes = {
        @Index(name = "idx_qalog_user", columnList = "user_id"),
        @Index(name = "idx_qalog_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class QaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 120)
    private String username;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "role_name", length = 60)
    private String roleName;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "retrieval_json", columnDefinition = "text")
    private String retrievalJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}
